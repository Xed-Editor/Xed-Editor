@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.agent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.StringWriter
import java.io.PrintWriter
import com.rk.ai.core.MessageRole
import com.rk.ai.core.ReasoningLevel
import com.rk.ai.models.Tool
import com.rk.ai.core.merge
import com.rk.ai.models.CustomBody
import com.rk.ai.providers.Model
import com.rk.ai.providers.Provider
import com.rk.ai.providers.ProviderManager
import com.rk.ai.providers.ProviderSetting
import com.rk.ai.providers.TextGenerationParams
import com.rk.ai.providers.registry.ModelRegistry
import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart
import com.rk.ai.models.ToolApprovalState
import com.rk.ai.models.ExecutionState
import com.rk.ai.models.handleMessageChunk
import com.rk.ai.models.limitContext
import com.rk.ai.agent.RecoveryEngine
import com.rk.ai.agent.transformers.InputMessageTransformer
import com.rk.ai.agent.transformers.MessageTransformer
import com.rk.ai.agent.transformers.OutputMessageTransformer
import com.rk.ai.agent.transformers.onGenerationFinish
import com.rk.ai.agent.transformers.transforms
import com.rk.ai.agent.transformers.visualTransforms
import com.rk.ai.agent.tools.buildMemoryTools
import com.rk.ai.persistence.settings.Settings
import com.rk.ai.persistence.settings.findModelById
import com.rk.ai.persistence.settings.findProvider
import com.rk.ai.models.Assistant
import com.rk.ai.models.AssistantMemory
import com.rk.ai.persistence.repo.ConversationRepository
import com.rk.ai.persistence.repo.MemoryRepository
import com.rk.ai.streaming.applyPlaceholders
import java.util.Locale
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

private const val TAG = "GenerationHandler"
private const val DOOM_LOOP_THRESHOLD = 3
private const val MAX_COMPACTIONS = 3
private const val MAX_TOOL_OUTPUT_CHARS = 10_000
private const val TOOL_OUTPUT_TRUNCATION_SUFFIX = "\n\n[Output truncated at $MAX_TOOL_OUTPUT_CHARS characters]"

@Serializable
sealed interface GenerationChunk {
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk

    data class CompactionNeeded(
        val reason: String,
    ) : GenerationChunk

    data class ToolStateChanged(
        val toolCallId: String,
        val toolName: String,
        val executionState: ExecutionState,
    ) : GenerationChunk

    data class StepStarted(
        val stepIndex: Int,
    ) : GenerationChunk

    data class StepFinished(
        val stepIndex: Int,
        val cost: Float = 0f,
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
        val reasoningTokens: Int = 0,
    ) : GenerationChunk

    data class GenerationError(
        val errorMessage: String,
        val errorType: String = "UnknownError",
    ) : GenerationChunk
}

class GenerationHandler(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val json: Json,
    private val memoryRepo: MemoryRepository,
    val conversationRepo: ConversationRepository,
    private val aiLoggingManager: AILoggingManager,
) {
    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        maxSteps: Int = 256,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
    ): Flow<GenerationChunk> = flow {
        // Per-call state — reset for each generateText() invocation to prevent bleed between sessions
        var compactionCount = 0
        var previousToolCalls: List<Pair<String, String>> = emptyList()
        var lastFinishReason: String? = null
        // Pattern-based doom loop detection: track recent tool name sequences
        val recentToolNameSequences = mutableListOf<List<String>>()
        val PATTERN_WINDOW = 6 // detect patterns across this many steps
        val PATTERN_REPEAT_THRESHOLD = 2 // break if pattern repeats this many times

        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = providerManager.getProviderByType(provider)

        var messages: List<UIMessage> = messages

        var consecutiveRepetitions = 0

        for (stepIndex in 0 until maxSteps) {
            lastFinishReason = null
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")
            emit(GenerationChunk.StepStarted(stepIndex))

            // Check for overflow before generating next step
            if (CompactionHandler.needsCompaction(messages, model.contextWindow, model.maxOutputTokens)) {
                Log.w(TAG, "Context overflow detected at step #$stepIndex, compacting...")
                if (compactionCount >= MAX_COMPACTIONS) {
                    Log.w(TAG, "Max compactions reached ($MAX_COMPACTIONS), cannot compact further")
                } else {
                    val compacted = compactMessages(messages, model)
                    messages = compacted
                    compactionCount++
                    emit(GenerationChunk.CompactionNeeded("context_overflow_after_step_$stepIndex"))
                    emit(GenerationChunk.Messages(compacted))
                    Log.i(TAG, "Compaction done, continuing with ${compacted.size} messages")
                }
            }

            val toolsInternal = buildList {
                Log.i(TAG, "generateInternal: build tools($assistant)")
                if (assistant.enableMemory) {
                    val memoryAssistantId = if (assistant.useGlobalMemory) {
                        MemoryRepository.GLOBAL_MEMORY_ID
                    } else {
                        assistant.id.toString()
                    }
                    buildMemoryTools(
                        json = json,
                        onCreation = { content ->
                            memoryRepo.addMemory(memoryAssistantId, content)
                        },
                        onUpdate = { id, content ->
                            memoryRepo.updateContent(id, content)
                        },
                        onDelete = { id ->
                            memoryRepo.deleteMemory(id)
                        }
                    ).let(this::addAll)
                }
                addAll(tools)
            }

            // Check if we have tool calls ready to continue after user interaction.
            val pendingTools = messages.lastOrNull()?.getTools()?.filter {
                it.canResumeExecution
            } ?: emptyList()

            val toolsToProcess: List<UIMessagePart.Tool>

            // Skip generation if we have approved/denied tool calls to handle
            if (pendingTools.isEmpty()) {
                lastFinishReason = generateInternal(
                    assistant = assistant,
                    settings = settings,
                    messages = messages,
                    onUpdateMessages = {
                        messages = it.transforms(
                            transformers = outputTransformers,
                            context = context,
                            model = model,
                            assistant = assistant,
                            settings = settings
                        )
                        emit(
                            GenerationChunk.Messages(
                                messages.visualTransforms(
                                    transformers = outputTransformers,
                                    context = context,
                                    model = model,
                                    assistant = assistant,
                                    settings = settings
                                )
                            )
                        )
                    },
                    transformers = inputTransformers,
                    model = model,
                    providerImpl = providerImpl,
                    provider = provider,
                    tools = toolsInternal,
                    memories = memories ?: emptyList(),
                    stream = assistant.streamOutput,
                    processingStatus = processingStatus,
                    conversationSystemPrompt = conversationSystemPrompt,
                    conversationModeInjectionIds = conversationModeInjectionIds,
                    conversationLorebookIds = conversationLorebookIds,
                )
                messages = messages.visualTransforms(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.onGenerationFinish(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.slice(0 until messages.lastIndex) + messages.last().copy(
                    finishedAt = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
                emit(GenerationChunk.Messages(messages))

                val tools = messages.last().getTools().filter { !it.isExecuted }
                if (tools.isEmpty()) {
                    val lastMessage = messages.lastOrNull()
                    val finishReason = lastMessage?.let { msg ->
                        msg.parts.firstOrNull()?.let { null }
                    }
                    // Check finish reason from the message's choices (handled via handleMessageChunk)
                    // If the model stopped with "length" finish reason, compact and retry
                    val hasLengthFinish = checkFinishReason(messages, lastFinishReason)
                    if (hasLengthFinish) {
                        Log.w(TAG, "Model stopped with 'length' finish reason, compacting...")
                        if (compactionCount < MAX_COMPACTIONS) {
                            val compacted = compactMessages(messages, model)
                            messages = compacted
                            compactionCount++
                            emit(GenerationChunk.CompactionNeeded("length_finish"))
                            emit(GenerationChunk.Messages(compacted))
                            continue
                        }
                    }
                    break
                }

                // Check for tools that need approval
                var hasPendingApproval = false
                val updatedTools = tools.map { tool ->
                    val toolDef = toolsInternal.find { it.name == tool.toolName }
                    when {
                        // Tool needs approval and state is Auto -> set to Pending
                        toolDef != null && toolDef.needsApproval && tool.approvalState is ToolApprovalState.Auto -> {
                            hasPendingApproval = true
                            tool.copy(approvalState = ToolApprovalState.Pending)
                        }
                        // State is Pending -> keep waiting
                        tool.approvalState is ToolApprovalState.Pending -> {
                            hasPendingApproval = true
                            tool
                        }

                        else -> tool
                    }
                }

                // If any tools were updated to Pending, update the message and break
                if (updatedTools != tools) {
                    val lastMessage = messages.last()
                    val updatedParts = lastMessage.parts.map { part ->
                        if (part is UIMessagePart.Tool) {
                            updatedTools.find { it.toolCallId == part.toolCallId } ?: part
                        } else {
                            part
                        }
                    }
                    messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
                    emit(GenerationChunk.Messages(messages))
                }

                // If there are pending approvals, break and wait for user
                if (hasPendingApproval) {
                    Log.i(TAG, "generateText: waiting for tool approval")
                    break
                }

                toolsToProcess = updatedTools
            } else {
                // Resuming after user interaction - use the resumable tools directly.
                Log.i(TAG, "generateText: resuming with ${pendingTools.size} resumable tools")
                toolsToProcess = messages.last().getTools().filter { it.canResumeExecution }
            }

            // Handle tools (sequential execution, preserving original order)
            val executedTools = arrayListOf<UIMessagePart.Tool>()
            toolsToProcess.forEach { tool ->
                when (tool.approvalState) {
                    is ToolApprovalState.Denied -> {
                        val reason = (tool.approvalState as ToolApprovalState.Denied).reason
                        val deniedTool = tool.copy(
                            executionState = ExecutionState.Error("Denied by user: ${reason.ifBlank { "No reason provided" }}"),
                            output = listOf(UIMessagePart.Text("Tool '${tool.toolName}' execution denied by user. Reason: ${reason.ifBlank { "No reason provided" }}"))
                        )
                        executedTools += deniedTool
                        emit(GenerationChunk.ToolStateChanged(tool.toolCallId, tool.toolName, deniedTool.executionState))
                    }
                    is ToolApprovalState.Answered -> {
                        val answer = (tool.approvalState as ToolApprovalState.Answered).answer
                        val answeredTool = tool.copy(
                            executionState = ExecutionState.Completed(title = "answered"),
                            output = listOf(UIMessagePart.Text(answer))
                        )
                        executedTools += answeredTool
                        emit(GenerationChunk.ToolStateChanged(tool.toolCallId, tool.toolName, answeredTool.executionState))
                    }
                    is ToolApprovalState.Pending -> { }
                    else -> {
                        emit(GenerationChunk.ToolStateChanged(tool.toolCallId, tool.toolName, ExecutionState.Running()))
                        val resultTool = executeSingleTool(tool, toolsInternal)
                        executedTools += resultTool
                        emit(GenerationChunk.ToolStateChanged(tool.toolCallId, tool.toolName, resultTool.executionState))
                    }
                }
            }

            if (executedTools.isEmpty()) {
                break
            }

            // Doom loop detection: check if same tool called repeatedly with same input
            val currentToolCalls = executedTools.map { it.toolName to it.input }
            if (currentToolCalls == previousToolCalls) {
                consecutiveRepetitions++
            } else {
                consecutiveRepetitions = 0
            }
            previousToolCalls = currentToolCalls
            if (consecutiveRepetitions >= DOOM_LOOP_THRESHOLD) {
                Log.w(TAG, "Doom loop detected: same tool calls repeated ${consecutiveRepetitions + 1} times")
                val doomTool = CompactionHandler.detectDoomLoop(messages)
                if (doomTool != null) {
                    Log.w(TAG, "Breaking doom loop for tool: $doomTool, injecting recovery hint")
                    val recoveryMsg = UIMessage(
                        role = MessageRole.SYSTEM,
                        parts = listOf(UIMessagePart.Text(
                            "[SYSTEM: The tool '$doomTool' has been called repeatedly with the same arguments. " +
                            "This appears to be a loop. Try a fundamentally different approach: " +
                            "check the tool's output more carefully, read the relevant file first, " +
                            "or try an alternative strategy. Do NOT call '$doomTool' again with the same input.]"
                        ))
                    )
                    messages = messages + recoveryMsg
                    emit(GenerationChunk.Messages(messages))
                    break
                }
            }

            // Pattern-based doom loop: detect repeated sequences of tool names (even with different args)
            val currentToolNames = executedTools.map { it.toolName }
            recentToolNameSequences.add(currentToolNames)
            if (recentToolNameSequences.size > PATTERN_WINDOW) {
                recentToolNameSequences.removeAt(0)
            }
            if (recentToolNameSequences.size >= 4) {
                // Check if the last N steps repeat a pattern
                val half = recentToolNameSequences.size / 2
                val firstHalf = recentToolNameSequences.take(half).flatten()
                val secondHalf = recentToolNameSequences.drop(half).flatten()
                if (firstHalf == secondHalf && firstHalf.isNotEmpty()) {
                    Log.w(TAG, "Pattern doom loop detected: tool sequence repeated ${recentToolNameSequences.size} times")
                    val toolsCalled = firstHalf.distinct().joinToString(", ")
                    val recoveryMsg = UIMessage(
                        role = MessageRole.SYSTEM,
                        parts = listOf(UIMessagePart.Text(
                            "[SYSTEM: The agent appears to be stuck in a loop calling the same tools repeatedly " +
                            "($toolsCalled). Stop calling these tools and provide a summary of what you have found so far. " +
                            "If you need more information, try a completely different approach or ask the user for guidance.]"
                        ))
                    )
                    messages = messages + recoveryMsg
                    emit(GenerationChunk.Messages(messages))
                    recentToolNameSequences.clear()
                    break
                }
            }

            // Auto-recovery: check for failed tools and attempt recovery
            val recoveryEngine = RecoveryEngine()
            val failedTools = executedTools.filter { it.executionState is ExecutionState.Error }
            var recoveryInjected = false
            for (failed in failedTools) {
                val errorMsg = (failed.executionState as? ExecutionState.Error)?.error ?: continue
                val action = recoveryEngine.analyzeFailure(failed.toolName, errorMsg, failed.input, null)
                if (action != null) {
                    when (action.action) {
                        "create_directory" -> {
                            val dirPath = action.params["path"]
                            if (dirPath != null) {
                                try {
                                    val dir = java.io.File(dirPath)
                                    dir.mkdirs()
                                    Log.i(TAG, "Recovery: created directory $dirPath")
                                    val recoveryMsg = UIMessage(
                                        role = MessageRole.SYSTEM,
                                        parts = listOf(UIMessagePart.Text(
                                            "[RECOVERY] Tool '${failed.toolName}' failed because directory '$dirPath' did not exist. " +
                                            "The directory has been auto-created. You may retry the tool call. " +
                                            "Action: ${action.message}"
                                        ))
                                    )
                                    messages = messages + recoveryMsg
                                    emit(GenerationChunk.Messages(messages))
                                    recoveryInjected = true
                                } catch (e: Exception) {
                                    Log.w(TAG, "Recovery failed for directory creation: $dirPath", e)
                                }
                            }
                        }
                        "skip_missing_file" -> {
                            val path = action.params["path"]
                            val recoveryMsg = UIMessage(
                                role = MessageRole.SYSTEM,
                                parts = listOf(UIMessagePart.Text(
                                    "[RECOVERY] File '$path' does not exist (requested by '${failed.toolName}'). " +
                                    "Report this as a missing file and try a different approach. ${action.message}"
                                ))
                            )
                            messages = messages + recoveryMsg
                            emit(GenerationChunk.Messages(messages))
                            recoveryInjected = true
                        }
                        else -> {
                            val recoveryMsg = recoveryEngine.buildRecoveryMessage(failed.toolName, errorMsg, action)
                            messages = messages + recoveryMsg
                            emit(GenerationChunk.Messages(messages))
                            recoveryInjected = true
                        }
                    }
                }
            }
            if (recoveryInjected) {
                Log.i(TAG, "Recovery injected, continuing agent loop")
                continue
            }

            // Update last message with executed tools
            val lastMessage = messages.last()
            val updatedParts = lastMessage.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    executedTools.find { it.toolCallId == part.toolCallId } ?: part
                } else part
            }
            messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
            emit(
                GenerationChunk.Messages(
                    messages.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                        settings = settings
                    )
                )
            )

            val tokenEstimate = TokenEstimator.estimate(messages)
            emit(GenerationChunk.StepFinished(
                stepIndex = stepIndex,
                cost = 0f,
                inputTokens = tokenEstimate,
                outputTokens = executedTools.sumOf { t ->
                    t.output.sumOf { p ->
                        when (p) {
                            is UIMessagePart.Text -> TokenEstimator.estimate(p.text)
                            else -> 0
                        }
                    }
                },
            ))
        }

    }.flowOn(Dispatchers.IO)

    private suspend fun compactMessages(
        messages: List<UIMessage>,
        model: Model,
    ): List<UIMessage> {
        val hasOverflow = CompactionHandler.needsCompaction(messages, model.contextWindow, model.maxOutputTokens)
        if (!hasOverflow) return messages

        val compacted = CompactionHandler.pruneMessages(messages)
        if (compacted.compactedMessages.isNotEmpty() && compacted.prunedCount > 0) {
            val summaryText = buildString {
                appendLine("[Compacted summary of earlier context]")
                appendLine("Previous ${compacted.compactedMessages.size} messages consumed ${compacted.prunedCount} tokens of tool output.")
                appendLine("The remaining context contains the most recent turns.")
                appendLine("Tool outputs in the compacted portion have been truncated to save context.")
            }
            val summaryMsg = UIMessage(
                role = MessageRole.SYSTEM,
                parts = listOf(UIMessagePart.Text(summaryText)),
            )
            val tailMessages = messages.drop(compacted.compactedMessages.size)
            return listOf(summaryMsg) + tailMessages
        }

        return TokenEstimator.truncateByTokens(
            messages,
            TokenEstimator.usableTokens(model.contextWindow, model.maxOutputTokens),
        )
    }

    private suspend fun executeSingleTool(
        tool: UIMessagePart.Tool,
        toolsInternal: List<Tool>,
    ): UIMessagePart.Tool {
        return try {
            val toolDef = toolsInternal.find { it.name == tool.toolName }
                ?: error("Tool ${tool.toolName} not found")
            val args = try {
                com.google.gson.JsonParser.parseString(tool.input.ifBlank { "{}" })
            } catch (e: Exception) {
                error("Invalid tool arguments JSON for ${tool.toolName}: ${e.message}")
            }
            Log.i(TAG, "executeSingleTool: ${toolDef.name} args: $args")
            val result = toolDef.execute(args)
            val truncatedOutput = result.map { part ->
                when (part) {
                    is UIMessagePart.Text -> {
                        val text = part.text
                        if (text.length > MAX_TOOL_OUTPUT_CHARS) {
                            part.copy(text = text.take(MAX_TOOL_OUTPUT_CHARS) + TOOL_OUTPUT_TRUNCATION_SUFFIX)
                        } else part
                    }
                    else -> part
                }
            }
            tool.copy(
                executionState = ExecutionState.Completed(title = toolDef.name),
                output = truncatedOutput
            )
        } catch (e: Exception) {
            Log.w(TAG, "executeSingleTool failed: ${tool.toolName}", e)
            tool.copy(
                executionState = ExecutionState.Error(e.message ?: "Unknown error"),
                output = listOf(UIMessagePart.Text("Error: ${e.message}"))
            )
        }
    }

    private fun checkFinishReason(messages: List<UIMessage>, finishReason: String?): Boolean {
        if (messages.isEmpty()) return false
        return finishReason == "length"
    }

    private suspend fun generateInternal(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        stream: Boolean,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
    ): String? {
        var finishReason: String? = null
        val internalMessages = buildList {
            val system = buildString {
                val effectiveSystemPrompt =
                    if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) {
                        conversationSystemPrompt
                    } else {
                        assistant.systemPrompt
                    }
                if (effectiveSystemPrompt.isNotBlank()) {
                    append(effectiveSystemPrompt)
                }

                // Memory
                if (assistant.enableMemory) {
                    appendLine()
                    append(buildMemoryPrompt(memories = memories))
                }
                if (assistant.enableRecentChatsReference) {
                    appendLine()
                    append(buildRecentChatsPrompt(assistant, conversationRepo))
                }

                // Tool prompts
                tools.forEach { tool ->
                    appendLine()
                    append(tool.systemPrompt(model.id, messages))
                }
            }
            if (system.isNotBlank()) add(UIMessage.system(prompt = system))
            addAll(messages.limitContext(assistant.contextMessageSize))
        }.transforms(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            settings = settings,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
            processingStatus = processingStatus,
        )

        var messages: List<UIMessage> = messages
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = tools,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(model.customBodies)
            }
        )
        if (stream) {
            aiLoggingManager.addLog(
                AILogging.Generation(
                    params = params,
                    messages = messages,
                    providerSetting = provider,
                    stream = true
                )
            )
            providerImpl.streamText(
                providerSetting = provider,
                messages = internalMessages,
                params = params
            ).collect { chunk ->
                finishReason = chunk.choices.getOrNull(0)?.finishReason
                messages = messages.handleMessageChunk(chunk = chunk, modelId = model.id)
                chunk.usage?.let { usage ->
                    messages = messages.mapIndexed { index, message ->
                        if (index == messages.lastIndex) {
                            message.copy(usage = message.usage.merge(usage))
                        } else {
                            message
                        }
                    }
                }
                onUpdateMessages(messages)
            }
        } else {
            aiLoggingManager.addLog(
                AILogging.Generation(
                    params = params,
                    messages = messages,
                    providerSetting = provider,
                    stream = false
                )
            )
            val chunk = providerImpl.generateText(
                providerSetting = provider,
                messages = internalMessages,
                params = params,
            )
            finishReason = chunk.choices.getOrNull(0)?.finishReason
            messages = messages.handleMessageChunk(chunk = chunk, modelId = model.id)
            chunk.usage?.let { usage ->
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex) {
                        message.copy(
                            usage = message.usage.merge(usage)
                        )
                    } else {
                        message
                    }
                }
            }
            onUpdateMessages(messages)
        }
        return finishReason
    }

    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        onStreamUpdate: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )

            var messages = listOf(UIMessage.user(prompt))
            var translatedText = ""

            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
                ),
            ).collect { chunk ->
                messages = messages.handleMessageChunk(chunk)
                translatedText = messages.lastOrNull()?.toText() ?: ""

                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val chunk = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    customBody = listOf(
                        CustomBody(
                            key = "translation_options",
                            value = buildJsonObject {
                                put("source_lang", JsonPrimitive("auto"))
                                put(
                                    "target_lang",
                                    JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH))
                                )
                            }
                        )
                    )
                ),
            )
            val translatedText = chunk.choices.firstOrNull()?.message?.toText() ?: ""

            if (translatedText.isNotBlank()) {
                onStreamUpdate?.invoke(translatedText)
                emit(translatedText)
            }
        }
    }.flowOn(Dispatchers.IO)
}
