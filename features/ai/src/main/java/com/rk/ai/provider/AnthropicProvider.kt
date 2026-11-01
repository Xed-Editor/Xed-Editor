package com.rk.ai.provider

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import androidx.compose.ui.graphics.vector.ImageVector

/** A provider backed by koog's Anthropic Messages client. */
class AnthropicProvider(
    override val id: String,
    override val displayName: String,
    override val icon: ImageVector,
    override val baseUrl: String = "https://api.anthropic.com",
    override val requestPath: String = "v1/messages",
    override val models: List<AiModel> = emptyList(),
) : AiProvider {
    override val llmProvider: LLMProvider = LLMProvider.Anthropic

    override fun createExecutor(config: AiProviderConfig): PromptExecutor {
        val settings =
            AnthropicClientSettings(
                modelVersionsMap = AnthropicClientSettings().modelVersionsMap + customModelVersion(config.modelId),
                baseUrl = config.baseUrl,
                messagesPath = config.requestPath,
            )
        return MultiLLMPromptExecutor(
            AnthropicLLMClient(
                apiKey = config.apiKey,
                settings = settings,
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
        )
    }

    /** The client resolves a model through [AnthropicClientSettings.modelVersionsMap]. */
    private fun customModelVersion(modelId: String): Map<LLModel, String> =
        mapOf(
            LLModel(provider = LLMProvider.Anthropic, id = modelId, capabilities = AiModel.DEFAULT_CAPABILITIES) to
                modelId
        )
}
