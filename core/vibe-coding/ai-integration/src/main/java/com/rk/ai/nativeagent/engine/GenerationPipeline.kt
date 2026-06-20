@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.engine

import android.util.Log
import com.rk.ai.agent.GenerationChunk
import com.rk.ai.agent.GenerationHandler
import com.rk.ai.agent.events.VibeCodingEvent
import com.rk.ai.agent.events.VibeCodingEventBus
import com.rk.ai.core.MessageRole
import com.rk.ai.models.ExecutionState
import com.rk.ai.models.Tool
import com.rk.ai.models.ToolApprovalState
import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "GenerationPipeline"
private const val MAX_AUTO_RESPOND_DEPTH = 5

data class GenerationConfig(
    val settings: com.rk.ai.persistence.settings.Settings,
    val model: com.rk.ai.providers.Model,
    val assistant: com.rk.ai.models.Assistant,
    val messages: List<UIMessage>,
    val tools: List<Tool>,
    val memories: List<com.rk.ai.models.AssistantMemory>?,
    val inputTransformers: List<com.rk.ai.agent.transformers.InputMessageTransformer>,
    val outputTransformers: List<com.rk.ai.agent.transformers.OutputMessageTransformer>,
)

class GenerationPipeline(
    private val generationHandler: GenerationHandler,
    private val permissionManager: PermissionManager,
    private val vibeEventBus: VibeCodingEventBus,
    private val engineScope: CoroutineScope,
    private val onStateUpdate: (VibeCodingState.() -> VibeCodingState) -> Unit,
    private val onSaveSession: () -> Unit,
    private val onSaveConversation: suspend () -> Unit,
    private val getState: () -> VibeCodingState,
) {
    private var currentJob: Job? = null
    private val autoRespondDepth = AtomicInteger(0)

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
    }

    fun isRunning(): Boolean = currentJob?.isActive == true

    fun execute(
        text: String,
        extraParts: List<UIMessagePart> = emptyList(),
        buildConfig: suspend () -> GenerationConfig?,
    ) {
        currentJob?.cancel()
        currentJob = engineScope.launch(Dispatchers.IO) {
            val trimmed = text.trim()

            onStateUpdate {
                copy(
                    isProcessing = true,
                    error = null,
                    messages = messages + UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Text(trimmed)) + extraParts,
                    ),
                )
            }

            engineScope.launch { vibeEventBus.emit(VibeCodingEvent.GenerationStarted) }

            val config = buildConfig() ?: return@launch

            runGeneration(config)

            onSaveConversation()
            onSaveSession()
            checkAndResume(buildConfig)
        }
    }

    fun resume(
        buildConfig: suspend () -> GenerationConfig? = { getState().let { null } },
    ) {
        currentJob?.cancel()
        currentJob = engineScope.launch(Dispatchers.IO) {
            val state = getState()
            if (state.messages.isEmpty()) {
                onStateUpdate { copy(isProcessing = false) }
                return@launch
            }

            onStateUpdate { copy(isProcessing = true, error = null) }
            engineScope.launch { vibeEventBus.emit(VibeCodingEvent.GenerationStarted) }

            val config = buildConfig() ?: return@launch

            runGeneration(config)

            onSaveSession()
            checkAndResume(buildConfig)
        }
    }

    private suspend fun runGeneration(config: GenerationConfig) {
        runCatching {
            generationHandler.generateText(
                settings = config.settings,
                model = config.model,
                messages = config.messages,
                assistant = config.assistant,
                memories = config.memories,
                tools = config.tools,
                inputTransformers = config.inputTransformers,
                outputTransformers = config.outputTransformers,
            ).collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        onStateUpdate { copy(messages = chunk.messages) }
                        onSaveSession()
                    }
                    is GenerationChunk.CompactionNeeded -> {
                        onStateUpdate { copy(compactionReason = chunk.reason) }
                    }
                    is GenerationChunk.ToolStateChanged -> {
                        val success = chunk.executionState is ExecutionState.Completed
                        onStateUpdate {
                            val record = ToolExecutionRecord(
                                toolName = chunk.toolName,
                                durationMs = 0,
                                success = success,
                                fromCache = false,
                            )
                            copy(toolExecutions = toolExecutions + record)
                        }
                        engineScope.launch {
                            vibeEventBus.emit(VibeCodingEvent.ToolExecuted(
                                sessionId = getState().activeSessionId ?: Uuid.random(),
                                toolName = chunk.toolName,
                                success = success,
                            ))
                        }
                    }
                    is GenerationChunk.GenerationError -> {
                        onStateUpdate { copy(error = chunk.errorMessage) }
                    }
                    is GenerationChunk.StepStarted -> {
                        onStateUpdate { copy(currentPhase = com.rk.ai.agent.executor.AgentPhase.EXECUTING) }
                    }
                    is GenerationChunk.StepFinished -> {
                        onStateUpdate { copy(currentPhase = com.rk.ai.agent.executor.AgentPhase.IDLE) }
                    }
                }
            }
        }.onFailure { e ->
            onStateUpdate { copy(isProcessing = false, error = e.message ?: "Generation failed") }
            engineScope.launch { vibeEventBus.emit(VibeCodingEvent.GenerationError) }
        }
    }

    private suspend fun checkAndResume(buildConfig: suspend () -> GenerationConfig?) {
        if (checkAndAutoRespondPermissions()) {
            resume(buildConfig)
        } else {
            onStateUpdate { copy(isProcessing = false) }
            vibeEventBus.emit(VibeCodingEvent.GenerationFinished)
        }
    }

    private fun checkAndAutoRespondPermissions(): Boolean {
        if (autoRespondDepth.get() >= MAX_AUTO_RESPOND_DEPTH) {
            Log.w(TAG, "Auto-respond depth limit reached ($MAX_AUTO_RESPOND_DEPTH), stopping recursion")
            autoRespondDepth.set(0)
            return false
        }
        val allPendingTools = getState().messages.flatMap { it.getTools() }.filter { it.isPending }
        if (allPendingTools.isEmpty()) {
            autoRespondDepth.set(0)
            return false
        }

        var didChange = false
        autoRespondDepth.incrementAndGet()
        for (tool in allPendingTools) {
            val action = permissionManager.getAction(tool.toolName, tool.input)
            when (action) {
                PermissionAction.ALLOW -> {
                    updateToolApproval(tool.toolCallId) { ToolApprovalState.Approved }
                    didChange = true
                }
                PermissionAction.DENY -> {
                    updateToolApproval(tool.toolCallId) { ToolApprovalState.Denied("Auto-denied by permission rule") }
                    didChange = true
                }
                else -> { }
            }
        }
        if (!didChange) autoRespondDepth.set(0)
        return didChange
    }

    private fun updateToolApproval(toolCallId: String, stateFn: () -> ToolApprovalState) {
        val messages = getState().messages.toMutableList()
        val lastIdx = messages.lastIndex
        if (lastIdx < 0) return
        val last = messages[lastIdx]
        val updatedParts = last.parts.map { part ->
            if (part is UIMessagePart.Tool && part.toolCallId == toolCallId) {
                part.copy(approvalState = stateFn())
            } else part
        }
        messages[lastIdx] = last.copy(parts = updatedParts)
        onStateUpdate { copy(messages = messages) }
        onSaveSession()
    }
}
