package com.rk.ai.provider

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

data class AiModel(
    val id: String,
    val providerId: String,
    val displayName: String = id,
    val capabilities: List<LLMCapability> = DEFAULT_CAPABILITIES,
    val contextWindowTokens: Int? = null,
) {
    fun toLLModel(provider: LLMProvider): LLModel = LLModel(provider = provider, id = id, capabilities = capabilities)

    companion object {
        /** The capabilities a tool-calling chat model needs. */
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
