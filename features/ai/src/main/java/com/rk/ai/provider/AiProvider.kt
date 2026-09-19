package com.rk.ai.provider

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider

/**
 * Everything needed to talk to one backend.
 *
 * @param apiKey the credential, or blank when the endpoint needs none.
 * @param baseUrl the endpoint root, already defaulted when the user left it empty.
 * @param chatCompletionsPath the path appended to [baseUrl] for chat completions.
 */
data class AiProviderConfig(
    val apiKey: String,
    val baseUrl: String,
    val chatCompletionsPath: String,
)

/**
 * A backend that can serve chat models.
 *
 * Implement this and register it with [AiProviderRegistry] to add a provider: the settings screen,
 * the model picker and the agent runtime all pick it up automatically. Providers usually also
 * declare their [models]; extra models can be added independently with [AiModelRegistry].
 */
interface AiProvider {
    /** Stable identifier persisted in settings; never change it once released. */
    val id: String

    /** Human-readable name for the settings UI. */
    val displayName: String

    /** Used when the configured base URL is blank. */
    val defaultBaseUrl: String

    /** Default chat-completions path for this provider. */
    val defaultChatCompletionsPath: String

    /** The koog provider that routes requests to this backend. */
    val llmProvider: LLMProvider

    /** Models shipped with the provider. */
    val models: List<AiModel>

    /** Builds the executor for a run. Called (and cached) by [AiProviderRuntime]. */
    fun createExecutor(config: AiProviderConfig): PromptExecutor

    /** Resolves the chat-completions path for [baseUrl]; defaults to [defaultChatCompletionsPath]. */
    fun resolveChatCompletionsPath(baseUrl: String): String = defaultChatCompletionsPath

    /** True when [baseUrl] clearly belongs to this provider, used to migrate older settings. */
    fun matchesBaseUrl(baseUrl: String): Boolean = false
}
