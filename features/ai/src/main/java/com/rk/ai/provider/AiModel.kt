package com.rk.ai.provider

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * A model an [AiProvider] can serve.
 *
 * Models are data, not code: an extension that wants to add, say, a new DeepSeek release registers
 * one through [AiModelRegistry] without touching the provider or the UI.
 */
data class AiModel(
    val id: String,
    val providerId: String,
    val displayName: String = id,
    val capabilities: List<LLMCapability> = DEFAULT_CAPABILITIES,
    val contextWindowTokens: Int? = null,
) {
    /** The koog model descriptor used for requests. */
    fun toLLModel(provider: LLMProvider): LLModel = LLModel(provider = provider, id = id, capabilities = capabilities)

    companion object {
        /** What a tool-calling chat model needs; providers may override it per model. */
        val DEFAULT_CAPABILITIES: List<LLMCapability> =
            listOf(
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.Completion,
                LLMCapability.OpenAIEndpoint.Completions,
            )
    }
}
