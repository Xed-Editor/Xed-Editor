package com.rk.ai.provider

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider

/** [apiKey] may be blank when the endpoint needs none. */
data class AiProviderConfig(
    val apiKey: String,
    val baseUrl: String,
    val chatCompletionsPath: String,
)

interface AiProvider {
    /** Stable identifier persisted in settings; never change it once released. */
    val id: String

    val displayName: String

    /** Used when the configured base URL is blank. */
    val defaultBaseUrl: String

    val defaultChatCompletionsPath: String

    val llmProvider: LLMProvider

    /** Models shipped with the provider. */
    val models: List<AiModel>

    /** Called and cached by [AiProviderRuntime]. */
    fun createExecutor(config: AiProviderConfig): PromptExecutor

    fun resolveChatCompletionsPath(baseUrl: String): String = defaultChatCompletionsPath

    /** Used to migrate older settings. */
    fun matchesBaseUrl(baseUrl: String): Boolean = false
}
