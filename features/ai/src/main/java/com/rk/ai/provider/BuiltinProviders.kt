package com.rk.ai.provider

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.ai.icons.AnthropicIcon
import com.rk.ai.icons.DeepSeekIcon
import com.rk.ai.icons.OpenAiIcon

object BuiltinProviders {
    const val DEEPSEEK_ID = "deepseek"
    const val OPENAI_ID = "openai"
    const val ANTHROPIC_ID = "anthropic"

    val openAi: AiProvider =
        OpenAiCompatibleProvider(
            id = OPENAI_ID,
            displayName = "OpenAI",
            icon = OpenAiIcon,
            baseUrl = "https://api.openai.com",
            requestPath = "v1/chat/completions",
            models =
                listOf(
                    AiModel(id = "gpt-4o", providerId = OPENAI_ID),
                    AiModel(id = "gpt-4o-mini", providerId = OPENAI_ID),
                    AiModel(id = "gpt-4.1", providerId = OPENAI_ID),
                    AiModel(id = "gpt-4.1-mini", providerId = OPENAI_ID),
                ),
        )

    val anthropic: AiProvider =
        AnthropicProvider(
            id = ANTHROPIC_ID,
            displayName = "Anthropic",
            icon = AnthropicIcon,
            models =
                listOf(
                    anthropicModel(AnthropicModels.Sonnet_4_6, "Claude Sonnet 4.6"),
                    anthropicModel(AnthropicModels.Sonnet_4_5, "Claude Sonnet 4.5"),
                    anthropicModel(AnthropicModels.Opus_4_7, "Claude Opus 4.7"),
                    anthropicModel(AnthropicModels.Opus_4_6, "Claude Opus 4.6"),
                    anthropicModel(AnthropicModels.Opus_4_5, "Claude Opus 4.5"),
                    anthropicModel(AnthropicModels.Haiku_4_5, "Claude Haiku 4.5"),
                ),
        )

    fun all(): List<AiProvider> = listOf(openAi, anthropic)

    fun default(): AiProvider = anthropic

    private fun anthropicModel(model: LLModel, displayName: String): AiModel =
        AiModel(
            id = model.id,
            providerId = ANTHROPIC_ID,
            displayName = displayName,
            capabilities = model.capabilities.orEmpty(),
            contextWindowTokens = model.contextLength?.toInt(),
            llmModel = model,
        )
}

/**
 * A provider that speaks the OpenAI chat-completions protocol. Pass [executorFactory] to keep the
 * protocol but change the client, or implement [AiProvider] directly for a different protocol.
 */
open class OpenAiCompatibleProvider(
    override val id: String,
    override val displayName: String,
    override val icon: ImageVector,
    override val baseUrl: String,
    override val requestPath: String = "v1/chat/completions",
    override val models: List<AiModel> = emptyList(),
    override val requiresApiKey: Boolean = true,
    private val executorFactory: ((AiProviderConfig) -> PromptExecutor)? = null,
) : AiProvider {
    override val llmProvider: LLMProvider = LLMProvider.OpenAI

    override fun createExecutor(config: AiProviderConfig): PromptExecutor =
        executorFactory?.invoke(config) ?: openAiExecutor(config)

    private fun openAiExecutor(config: AiProviderConfig): PromptExecutor =
        MultiLLMPromptExecutor(
            OpenAILLMClient(
                apiKey = config.apiKey,
                settings =
                    OpenAIClientSettings(
                        baseUrl = config.baseUrl,
                        chatCompletionsPath = config.requestPath,
                    ),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
        )
}
