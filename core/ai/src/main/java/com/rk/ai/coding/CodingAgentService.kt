package com.rk.ai.coding

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.agent.GenerationChunk
import com.rk.ai.agent.GenerationHandler
import com.rk.ai.coding.context.ContextBuilder
import com.rk.ai.coding.context.WorkspaceContext
import com.rk.ai.coding.tools.NativeToolContext
import com.rk.ai.coding.tools.ToolApprovalRequest
import com.rk.ai.coding.tools.ToolRegistry
import com.rk.ai.models.ToolApprovalState
import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart
import com.rk.ai.persistence.settings.findModelById
import com.rk.ai.persistence.settings.getCurrentAssistant
import com.rk.ai.service.IdeService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CodingAgentSession internal constructor(
    val id: String,
    initialMessages: List<UIMessage>,
) {
    @Volatile var messages: List<UIMessage> = initialMessages
    @Volatile var status: CodingAgentSessionStatus = CodingAgentSessionStatus.Idle
    @Volatile var updatedAt: Long = System.currentTimeMillis()
    val createdAt: Long = updatedAt
    internal var job: Job? = null

    fun snapshot(): CodingAgentSessionSnapshot =
        CodingAgentSessionSnapshot(
            id = id,
            status = status,
            messages = messages,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}

class CodingAgentService(
    private val generationRunner: CodingGenerationRunner,
    private val ideService: IdeService,
    private val contextBuilder: ContextBuilder,
    private val toolRegistry: ToolRegistry,
) {
    constructor(
        generationHandler: GenerationHandler,
        ideService: IdeService,
        contextBuilder: ContextBuilder = ContextBuilder(ideService),
        toolRegistry: ToolRegistry = ToolRegistry(),
    ) : this(
        generationRunner = GenerationHandlerRunner(generationHandler),
        ideService = ideService,
        contextBuilder = contextBuilder,
        toolRegistry = toolRegistry,
    )

    private val sessions = ConcurrentHashMap<String, CodingAgentSession>()
    private val toolContext = NativeToolContext(ideService)

    fun createSession(history: List<UIMessage> = emptyList()): CodingAgentSession {
        val id = UUID.randomUUID().toString()
        val session = CodingAgentSession(id, history)
        sessions[id] = session
        return session
    }

    fun getSession(sessionId: String): CodingAgentSessionSnapshot? = sessions[sessionId]?.snapshot()

    fun cancelSession(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        session.status = CodingAgentSessionStatus.Cancelled
        session.updatedAt = System.currentTimeMillis()
        session.job?.cancel(CancellationException("Coding agent session cancelled"))
        return true
    }

    fun approveTool(sessionId: String, toolCallId: String): Boolean =
        updateToolApproval(sessionId, toolCallId) { it.copy(approvalState = ToolApprovalState.Approved) }

    fun denyTool(sessionId: String, toolCallId: String, reason: String = ""): Boolean =
        updateToolApproval(sessionId, toolCallId) { it.copy(approvalState = ToolApprovalState.Denied(reason)) }

    suspend fun pendingApprovals(sessionId: String): List<ToolApprovalRequest> {
        val session = sessions[sessionId] ?: return emptyList()
        return pendingApprovals(session)
    }

    fun stream(request: CodingAgentRequest): Flow<CodingAgentEvent> = flow {
        val session = request.sessionId
            ?.let { sessions[it] ?: CodingAgentSession(it, request.history).also { created -> sessions[it] = created } }
            ?: createSession(request.history)
        session.job = currentCoroutineContext()[Job]
        session.status = CodingAgentSessionStatus.Running
        session.updatedAt = System.currentTimeMillis()
        emit(CodingAgentEvent.SessionStarted(session.id))

        try {
            val workspaceContext = contextBuilder.build(request.userText, request.contextOptions)
            emit(CodingAgentEvent.ContextBuilt(session.id, workspaceContext))

            val settings = request.settings
            val model = request.model ?: settings.findModelById(settings.chatModelId)
                ?: error("No model configured for coding agent")
            val assistant = request.assistant ?: settings.getCurrentAssistant()
            val generationMessages = prepareMessages(session, request)
            val tools = toolRegistry.asGenerationTools(toolContext)
            val systemPrompt = buildSystemPrompt(assistant.systemPrompt, workspaceContext)
            val runtimeAssistant = assistant.copy(allowConversationSystemPrompt = true)

            generationRunner.generateText(
                settings = settings,
                model = model,
                messages = generationMessages,
                assistant = runtimeAssistant,
                tools = tools,
                maxSteps = request.maxSteps,
                conversationSystemPrompt = systemPrompt,
            ).collect { chunk ->
                currentCoroutineContext().ensureActive()
                if (session.status == CodingAgentSessionStatus.Cancelled) throw CancellationException("session cancelled")
                if (chunk is GenerationChunk.Messages) {
                    session.messages = chunk.messages
                    session.updatedAt = System.currentTimeMillis()
                    val approvals = pendingApprovals(session)
                    session.status = if (approvals.isEmpty()) {
                        CodingAgentSessionStatus.Running
                    } else {
                        CodingAgentSessionStatus.WaitingForApproval
                    }
                    emit(CodingAgentEvent.Messages(session.id, chunk.messages, approvals))
                }
            }

            if (session.status != CodingAgentSessionStatus.WaitingForApproval) {
                session.status = CodingAgentSessionStatus.Completed
                session.updatedAt = System.currentTimeMillis()
                emit(CodingAgentEvent.Completed(session.id, session.messages))
            }
        } catch (e: CancellationException) {
            session.status = CodingAgentSessionStatus.Cancelled
            session.updatedAt = System.currentTimeMillis()
            emit(CodingAgentEvent.Cancelled(session.id))
        } catch (t: Throwable) {
            session.status = CodingAgentSessionStatus.Failed
            session.updatedAt = System.currentTimeMillis()
            emit(CodingAgentEvent.Error(session.id, t))
        } finally {
            session.job = null
        }
    }

    private fun prepareMessages(session: CodingAgentSession, request: CodingAgentRequest): List<UIMessage> {
        val base = if (session.messages.isEmpty()) request.history else session.messages
        if (request.resumePendingTools || request.userText.isBlank()) {
            session.messages = base
            return base
        }
        val updated = base + UIMessage.user(request.userText)
        session.messages = updated
        session.updatedAt = System.currentTimeMillis()
        return updated
    }

    private suspend fun pendingApprovals(session: CodingAgentSession): List<ToolApprovalRequest> {
        val pendingTools = session.messages.flatMap { message ->
            message.getTools().filter { it.approvalState is ToolApprovalState.Pending }
        }
        return pendingTools.map { tool ->
            val args = parseToolInput(tool.input)
            toolRegistry.permissionManager().buildPreview(
                sessionId = session.id,
                toolCallId = tool.toolCallId,
                toolName = tool.toolName,
                input = args,
                rawInput = tool.input,
                context = toolContext,
            )
        }
    }

    private fun updateToolApproval(
        sessionId: String,
        toolCallId: String,
        transform: (UIMessagePart.Tool) -> UIMessagePart.Tool,
    ): Boolean {
        val session = sessions[sessionId] ?: return false
        var changed = false
        session.messages = session.messages.map { message ->
            val parts = message.parts.map { part ->
                if (part is UIMessagePart.Tool && part.toolCallId == toolCallId) {
                    changed = true
                    transform(part)
                } else {
                    part
                }
            }
            if (parts == message.parts) message else message.copy(parts = parts)
        }
        if (changed) {
            session.updatedAt = System.currentTimeMillis()
            session.status = CodingAgentSessionStatus.Idle
        }
        return changed
    }

    private fun parseToolInput(input: String): JsonObject =
        runCatching { JsonParser.parseString(input.ifBlank { "{}" }).asJsonObject }
            .getOrDefault(JsonObject())

    private fun buildSystemPrompt(basePrompt: String, workspaceContext: WorkspaceContext): String {
        val contextPrompt = workspaceContext.toPrompt()
        return buildString {
            if (basePrompt.isNotBlank()) appendLine(basePrompt.trim())
            if (contextPrompt.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                appendLine(contextPrompt)
                appendLine()
                appendLine("Use the workspace context only when it is relevant. Prefer indexed search and targeted file reads over broad project reads.")
            }
        }.trim()
    }
}
