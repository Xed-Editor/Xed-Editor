package com.rk.ai.provider

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider

/**
 * The providers the app ships with.
 *
 * All three speak the OpenAI chat-completions protocol, which is why one implementation with a small
 * configuration object is enough. A provider with a different protocol implements [AiProvider]
 * directly and is registered the same way.
 */
object BuiltinProviders {
    const val DEEPSEEK_ID = "deepseek"
    const val OPENAI_ID = "openai"
    const val OPENAI_COMPATIBLE_ID = "openai-compatible"

    val deepseek: AiProvider =
        OpenAiCompatibleProvider(
            id = DEEPSEEK_ID,
            displayName = "DeepSeek",
            defaultBaseUrl = "https://api.deepseek.com",
            defaultChatCompletionsPath = "chat/completions",
            models =
                listOf(
                    AiModel(id = "deepseek-chat", providerId = DEEPSEEK_ID, displayName = "deepseek-chat (tools)"),
                    AiModel(id = "deepseek-reasoner", providerId = DEEPSEEK_ID, displayName = "deepseek-reasoner (thinking)"),
                ),
            baseUrlMatcher = { it.contains("deepseek", ignoreCase = true) },
        )

    val openAi: AiProvider =
        OpenAiCompatibleProvider(
            id = OPENAI_ID,
            displayName = "OpenAI",
            defaultBaseUrl = "https://api.openai.com",
            defaultChatCompletionsPath = "v1/chat/completions",
            models =
                listOf(
                    AiModel(id = "gpt-4o", providerId = OPENAI_ID),
                    AiModel(id = "gpt-4o-mini", providerId = OPENAI_ID),
                    AiModel(id = "gpt-4.1", providerId = OPENAI_ID),
                    AiModel(id = "gpt-4.1-mini", providerId = OPENAI_ID),
                ),
            baseUrlMatcher = { it.contains("api.openai.com", ignoreCase = true) },
        )

    /**
     * Any other OpenAI-compatible endpoint. It never auto-matches a base URL: the user selects it
     * explicitly and types the model id the endpoint expects.
     */
    val openAiCompatible: AiProvider =
        OpenAiCompatibleProvider(
            id = OPENAI_COMPATIBLE_ID,
            displayName = "OpenAI-compatible (custom)",
            defaultBaseUrl = "https://api.deepseek.com",
            defaultChatCompletionsPath = "v1/chat/completions",
            models = emptyList(),
            // Some gateways keep the vendor-specific `chat/completions`, others the canonical
            // `v1/chat/completions`; the historical heuristic is preserved so existing setups work.
            pathResolver = { baseUrl -> if (baseUrl.contains("deepseek", ignoreCase = true)) "chat/completions" else "v1/chat/completions" },
        )

    fun all(): List<AiProvider> = listOf(deepseek, openAi, openAiCompatible)
}

/**
 * An [AiProvider] backed by koog's OpenAI client.
 *
 * @param baseUrlMatcher decides whether an existing base URL belongs to this provider.
 * @param pathResolver overrides how the chat-completions path is derived from the base URL.
 */
class OpenAiCompatibleProvider(
    override val id: String,
    override val displayName: String,
    override val defaultBaseUrl: String,
    override val defaultChatCompletionsPath: String,
    override val models: List<AiModel>,
    private val baseUrlMatcher: (String) -> Boolean = { false },
    private val pathResolver: ((String) -> String)? = null,
) : AiProvider {
    override val llmProvider: LLMProvider = LLMProvider.OpenAI

    override fun createExecutor(config: AiProviderConfig): PromptExecutor =
        MultiLLMPromptExecutor(
            OpenAILLMClient(
                apiKey = config.apiKey,
                settings =
                    OpenAIClientSettings(
                        baseUrl = config.baseUrl,
                        chatCompletionsPath = config.chatCompletionsPath,
                    ),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
        )

    override fun resolveChatCompletionsPath(baseUrl: String): String =
        pathResolver?.invoke(baseUrl) ?: defaultChatCompletionsPath

    override fun matchesBaseUrl(baseUrl: String): Boolean = baseUrlMatcher.invoke(baseUrl)
}
