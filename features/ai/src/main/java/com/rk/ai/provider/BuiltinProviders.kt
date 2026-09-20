package com.rk.ai.provider

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider

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

    /** Never auto-matches a base URL; the user selects it explicitly. */
    val openAiCompatible: AiProvider =
        OpenAiCompatibleProvider(
            id = OPENAI_COMPATIBLE_ID,
            displayName = "OpenAI-compatible (custom)",
            defaultBaseUrl = "https://api.deepseek.com",
            defaultChatCompletionsPath = "v1/chat/completions",
            models = emptyList(),
            // Historical heuristic kept so existing setups keep working.
            pathResolver = { baseUrl -> if (baseUrl.contains("deepseek", ignoreCase = true)) "chat/completions" else "v1/chat/completions" },
        )

    fun all(): List<AiProvider> = listOf(deepseek, openAi, openAiCompatible)
}

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
