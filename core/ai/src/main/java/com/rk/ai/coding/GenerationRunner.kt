@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.rk.ai.coding

import com.rk.ai.agent.GenerationChunk
import com.rk.ai.agent.GenerationHandler
import com.rk.ai.agent.transformers.InputMessageTransformer
import com.rk.ai.agent.transformers.OutputMessageTransformer
import com.rk.ai.models.Assistant
import com.rk.ai.models.AssistantMemory
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessage
import com.rk.ai.persistence.settings.Settings
import com.rk.ai.providers.Model
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface CodingGenerationRunner {
    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        maxSteps: Int = 64,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
    ): Flow<GenerationChunk>
}

class GenerationHandlerRunner(
    private val generationHandler: GenerationHandler,
) : CodingGenerationRunner {
    override fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer>,
        outputTransformers: List<OutputMessageTransformer>,
        assistant: Assistant,
        memories: List<AssistantMemory>?,
        tools: List<Tool>,
        maxSteps: Int,
        processingStatus: MutableStateFlow<String?>,
        conversationSystemPrompt: String?,
        conversationModeInjectionIds: Set<Uuid>,
        conversationLorebookIds: Set<Uuid>,
    ): Flow<GenerationChunk> =
        generationHandler.generateText(
            settings = settings,
            model = model,
            messages = messages,
            inputTransformers = inputTransformers,
            outputTransformers = outputTransformers,
            assistant = assistant,
            memories = memories,
            tools = tools,
            maxSteps = maxSteps,
            processingStatus = processingStatus,
            conversationSystemPrompt = conversationSystemPrompt,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
        )
}
