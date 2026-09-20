package com.rk.ai.provider

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import androidx.compose.ui.graphics.vector.ImageVector

/** [apiKey] is the credential the provider asked for, and is blank when it needs none. */
data class AiProviderConfig(
    val apiKey: String,
    val baseUrl: String,
    val requestPath: String,
    val modelId: String,
)

interface AiProvider {
    /** Stable identifier persisted in settings; never change it once released. */
    val id: String

    val displayName: String

    /** Shown next to the name in the provider picker. */
    val icon: ImageVector

    /** Fixed endpoint root, owned by the provider. */
    val baseUrl: String

    /** Path appended to [baseUrl] for the provider's request endpoint. */
    val requestPath: String

    /** False when the provider authenticates itself or needs no credentials. */
    val requiresApiKey: Boolean get() = true

    val llmProvider: LLMProvider

    /** Models shipped with the provider. */
    val models: List<AiModel>

    /** Called and cached by [AiProviderRuntime]. */
    fun createExecutor(config: AiProviderConfig): PromptExecutor
}
