package com.rk.ai.chat

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.ai.provider.AiProvider
import com.rk.ai.settings.AiSettings
import com.rk.ai.settings.PermissionMode
import com.rk.ai.tools.AiTool
import com.rk.ai.tools.AiToolKind
import com.rk.ai.tools.AiWorkspace
import com.rk.ai.tools.BuiltinTools
import com.rk.ai.tools.toDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

class AiChatController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tools: List<AiTool> = BuiltinTools.all()
    private val toolsByName: Map<String, AiTool> = tools.associateBy { it.name }
    private val history = mutableListOf<AiTurn>()

    private var nextId = 0L
    private var runJob: Job? = null
    private var approvalDeferred: CompletableDeferred<Boolean>? = null

    private val approvedPaths = mutableSetOf<String>()

    val messages = mutableStateListOf<AiChatMessage>()

    var isRunning by mutableStateOf(false)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    var pendingApproval by mutableStateOf<PendingApproval?>(null)
        private set

    fun send(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || isRunning) return

        if (!AiSettings.hasApiKey) {
            val message = "No API key configured. Open AI settings and add one."
            Log.w(TAG, message)
            lastError = message
            return
        }

        lastError = null
        messages.add(AiChatMessage(id = nextId++, role = AiChatRole.User, text = text))

        isRunning = true
        runJob =
            scope.launch {
                try {
                    runAgent(text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val message = e.message ?: e::class.simpleName ?: "Request failed"
                    Log.e(TAG, "Agent run failed: $message", e)
                    lastError = message
                } finally {
                    isRunning = false
                    runJob = null
                    pendingApproval = null
                    approvalDeferred = null
                }
            }
    }

    fun approve(allow: Boolean) {
        approvalDeferred?.complete(allow)
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        isRunning = false
        pendingApproval = null
        approvalDeferred?.cancel()
        approvalDeferred = null
        messages.lastOrNull()?.let { last ->
            if (last.isStreaming) {
                replace(last.id) { it.copy(isStreaming = false) }
            }
        }
    }

    fun clear() {
        stop()
        history.clear()
        messages.clear()
        approvedPaths.clear()
        lastError = null
    }

    fun dispose() {
        scope.cancel()
    }

    private suspend fun runAgent(userText: String) {
        history.add(AiTurn.User(userText))

        repeat(MAX_STEPS) {
            val assistant = streamAssistant()
            history.add(assistant)
            if (assistant.toolCalls.isEmpty()) return

            assistant.toolCalls.forEach { call ->
                val outcome = executeTool(call)
                history.add(AiTurn.ToolOutput(outcome.id, call.name, outcome.output, outcome.isError))
            }
        }

        val message = "Stopped after $MAX_STEPS steps without a final answer."
        Log.w(TAG, message)
        lastError = message
    }

    private suspend fun streamAssistant(): AiTurn.Assistant {
        val conversation = buildPrompt()
        val messageId = nextId++
        messages.add(AiChatMessage(id = messageId, role = AiChatRole.Assistant, isStreaming = true))

        val toolCalls = LinkedHashMap<String, AiToolCall>()
        AiProvider.executor()
            .executeStreaming(conversation, AiProvider.model(), tools.map { it.toDescriptor() })
            .collect { frame ->
                when (frame) {
                    is StreamFrame.TextDelta -> appendText(messageId, frame.text)
                    is StreamFrame.ReasoningDelta -> frame.text?.let { appendText(messageId, it) }
                    is StreamFrame.ToolCallComplete -> {
                        val key = frame.id ?: "index-${frame.index ?: toolCalls.size}"
                        val callId = frame.id ?: toolCalls[key]?.id ?: UUID.randomUUID().toString()
                        toolCalls[key] = AiToolCall(callId, frame.name, frame.content)
                    }
                    is StreamFrame.End -> finish(messageId)
                    else -> Unit
                }
            }
        finish(messageId)

        if (toolCalls.isNotEmpty()) {
            Log.i(
                TAG,
                "Model requested ${toolCalls.size} tool call(s): " +
                    toolCalls.values.joinToString { it.name },
            )
        }

        return AiTurn.Assistant(
            text = messages.firstOrNull { it.id == messageId }?.text.orEmpty(),
            toolCalls = toolCalls.values.toList(),
        )
    }

    private suspend fun executeTool(call: AiToolCall): ToolOutcome {
        val tool =
            toolsByName[call.name]
                ?: run {
                    Log.e(TAG, "Model requested unknown tool '${call.name}'")
                    return ToolOutcome(call.id, "Unknown tool '${call.name}'.", true)
                }

        val mode = AiSettings.currentPermissionMode()
        val args = parseArgs(call.args)
        val target = targetKey(tool, args)
        val alreadyApproved = target != null && target in approvedPaths
        val diff = computeDiff(tool, args)

        when (gateFor(tool, mode, alreadyApproved = alreadyApproved)) {
            ToolGate.RunNow -> {
                if (!tool.isDestructive) {
                    Log.i(TAG, "Auto-running non-destructive tool '${tool.name}'")
                } else if (alreadyApproved) {
                    Log.i(TAG, "Auto-running '${tool.name}': '$target' was already allowed in this chat")
                }
                val status = if (alreadyApproved) "Running (already allowed for this file)…" else "Running…"
                val id = addToolMessage(tool, call.args, ToolCallStatus.Running, status, diff)
                return runTool(tool, call, args, id)
            }

            ToolGate.Blocked -> {
                Log.i(TAG, "Blocked tool '${tool.name}' (plan mode)")
                val id =
                    addToolMessage(
                        tool,
                        call.args,
                        ToolCallStatus.Denied,
                        "Plan mode: writes and shell commands are disabled.",
                        diff,
                    )
                return ToolOutcome(call.id, text(id), true)
            }

            ToolGate.AskUser -> Unit
        }

        val id =
            addToolMessage(
                tool,
                call.args,
                ToolCallStatus.AwaitingApproval,
                "Waiting for approval…",
                diff,
            )
        Log.i(TAG, "Tool '${tool.name}' awaiting approval (mode=$mode, args=${call.args.snippet()})")
        val deferred = CompletableDeferred<Boolean>()
        approvalDeferred = deferred
        pendingApproval = PendingApproval(tool.name, call.args, tool.description, diff, target)
        val allowed =
            try {
                deferred.await()
            } finally {
                pendingApproval = null
                approvalDeferred = null
            }

        if (!allowed) {
            Log.i(TAG, "Tool '${tool.name}' denied by user")
            replace(id) { it.copy(toolStatus = ToolCallStatus.Denied, text = "Denied by user.") }
            return ToolOutcome(call.id, "Permission denied by the user.", true)
        }

        if (target != null) {
            approvedPaths.add(target)
            Log.i(TAG, "File '$target' allowed for the rest of this chat")
        }

        replace(id) { it.copy(toolStatus = ToolCallStatus.Running, text = "Running…") }
        return runTool(tool, call, args, id)
    }

    private fun targetKey(tool: AiTool, args: JsonObject): String? {
        val path = tool.targetPath?.invoke(args)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { AiWorkspace.normalizePath(path) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun runTool(
        tool: AiTool,
        call: AiToolCall,
        args: JsonObject,
        messageId: Long,
    ): ToolOutcome {
        Log.i(TAG, "Running tool '${tool.name}' with args=${call.args.snippet()}")
        return try {
            val output = withContext(Dispatchers.IO) { tool.execute(args) }
            replace(messageId) { it.copy(toolStatus = ToolCallStatus.Success, text = output) }
            Log.i(TAG, "Tool '${tool.name}' succeeded (${output.length} chars)")
            ToolOutcome(call.id, output, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e::class.simpleName ?: "Tool failed"
            Log.e(TAG, "Tool '${tool.name}' failed: $message", e)
            replace(messageId) { it.copy(toolStatus = ToolCallStatus.Failed, text = message) }
            ToolOutcome(call.id, message, true)
        }
    }

    private fun buildPrompt(): Prompt =
        buildConversationPrompt(systemPrompt = AiSettings.systemPrompt, history = history)

    private suspend fun computeDiff(tool: AiTool, args: JsonObject): String? {
        val preview = tool.preview ?: return null
        return runCatching { withContext(Dispatchers.IO) { preview(args) } }
            .onFailure { Log.w(TAG, "Diff preview failed for tool '${tool.name}'", it) }
            .getOrNull()
    }

    private fun addToolMessage(
        tool: AiTool,
        args: String,
        status: ToolCallStatus,
        text: String,
        diff: String? = null,
    ): Long {
        val id = nextId++
        messages.add(
            AiChatMessage(
                id = id,
                role = AiChatRole.Tool,
                text = text,
                toolName = tool.name,
                toolArgs = args,
                toolStatus = status,
                diff = diff,
            )
        )
        return id
    }

    private fun parseArgs(args: String): JsonObject {
        if (args.isBlank()) return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(args).jsonObject }
            .onFailure {
                Log.w(TAG, "Malformed tool arguments, using empty object: ${args.snippet()}", it)
            }
            .getOrElse { JsonObject(emptyMap()) }
    }

    private fun String.snippet(): String =
        if (length <= SNIPPET_CHARS) this else take(SNIPPET_CHARS) + "…($length chars)"

    private fun appendText(id: Long, chunk: String) {
        if (chunk.isEmpty()) return
        replace(id) { it.copy(text = it.text + chunk) }
    }

    private fun finish(id: Long) {
        replace(id) { it.copy(isStreaming = false) }
    }

    private fun text(id: Long): String = messages.firstOrNull { it.id == id }?.text.orEmpty()

    private inline fun replace(id: Long, transform: (AiChatMessage) -> AiChatMessage) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            messages[index] = transform(messages[index])
        }
    }

    private data class ToolOutcome(val id: String, val output: String, val isError: Boolean)

    private companion object {
        const val MAX_STEPS = 8

        const val TAG = "XedAI"

        const val SNIPPET_CHARS = 500
    }
}

internal fun buildConversationPrompt(systemPrompt: String, history: List<AiTurn>): Prompt =
    prompt("xed-ai-chat") {
        system(systemPrompt)
        history.forEach { turn ->
            when (turn) {
                is AiTurn.User -> user(turn.text)
                is AiTurn.Assistant -> {
                    val parts = turn.toResponseParts()
                    if (parts.isNotEmpty()) assistant(parts)
                }
                is AiTurn.ToolOutput -> toolResult(turn.name, turn.output, turn.id, turn.isError)
            }
        }
    }

internal fun AiTurn.Assistant.toResponseParts(): List<MessagePart.ResponsePart> = buildList {
    if (text.isNotBlank()) add(MessagePart.Text(text))
    toolCalls.forEach { call ->
        add(MessagePart.Tool.Call(id = call.id, tool = call.name, args = call.args))
    }
}

internal enum class ToolGate {
    RunNow,

    AskUser,

    Blocked,
}

internal fun gateFor(
    tool: AiTool,
    mode: PermissionMode,
    alreadyApproved: Boolean = false,
): ToolGate =
    when {
        !tool.isDestructive -> ToolGate.RunNow
        mode == PermissionMode.PLAN -> ToolGate.Blocked
        alreadyApproved -> ToolGate.RunNow
        mode == PermissionMode.ASK -> ToolGate.AskUser
        mode == PermissionMode.ACCEPT_EDITS ->
            if (tool.kind == AiToolKind.Write) ToolGate.RunNow else ToolGate.AskUser
        else -> ToolGate.RunNow
    }
