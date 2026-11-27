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
import com.rk.ai.icons.GeminiIcon
import com.rk.ai.icons.LmStudioIcon
import com.rk.ai.icons.MistralIcon
import com.rk.ai.icons.OllamaIcon
import com.rk.ai.icons.OpenAiIcon
import com.rk.ai.icons.OpenRouterIcon
import com.rk.ai.icons.PerplexityIcon

/**
 * The providers that ship with the app. Everything that speaks the OpenAI chat-completions protocol shares
 * [OpenAiCompatibleProvider]; only Anthropic needs its own client.
 */
object BuiltinProviders {
    const val OPENAI_ID = "openai"
    const val ANTHROPIC_ID = "anthropic"
    const val DEEPSEEK_ID = "deepseek"
    const val GEMINI_ID = "gemini"
    const val OPENROUTER_ID = "openrouter"
    const val MISTRAL_ID = "mistral"
    const val PERPLEXITY_ID = "perplexity"
    const val OLLAMA_ID = "ollama"
    const val LM_STUDIO_ID = "lmstudio"

    val deepSeek: AiProvider =
        chatProvider(
            id = DEEPSEEK_ID,
            displayName = "DeepSeek",
            icon = DeepSeekIcon,
            baseUrl = "https://api.deepseek.com",
            requestPath = "chat/completions",
            models =
                modelsOf(
                    DEEPSEEK_ID,
                    "deepseek-flash" to "DeepSeek Flash",
                    "deepseek-v4-pro" to "DeepSeek V4 Pro",
                ),
        )

    val openAi: AiProvider =
        chatProvider(
            id = OPENAI_ID,
            displayName = "OpenAI",
            icon = OpenAiIcon,
            baseUrl = "https://api.openai.com",
            requestPath = "v1/chat/completions",
            models =
                modelsOf(
                    OPENAI_ID,
                    "gpt-5.4" to "GPT-5.4",
                    "gpt-5.4-mini" to "GPT-5.4 mini",
                    "gpt-5.4-nano" to "GPT-5.4 nano",
                    "gpt-5.2" to "GPT-5.2",
                    "gpt-4.1" to "GPT-4.1",
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

    val gemini: AiProvider =
        chatProvider(
            id = GEMINI_ID,
            displayName = "Google Gemini",
            icon = GeminiIcon,
            baseUrl = "https://generativelanguage.googleapis.com",
            // Gemini serves an OpenAI-compatible layer under /v1beta/openai.
            requestPath = "v1beta/openai/chat/completions",
            models =
                modelsOf(
                    GEMINI_ID,
                    "gemini-4-hydrogen" to "Gemini 4 Hydrogen",
                    "gemini-4-neon" to "Gemini 4 Neon",
                    "gemini-4-argon" to "Gemini 4 Argon",
                    "gemini-3.8-flash" to "Gemini 3.8 Flash",
                    "gemini-2.5-flash" to "Gemini 2.5 Flash",
                ),
        )

    val openRouter: AiProvider =
        chatProvider(
            id = OPENROUTER_ID,
            displayName = "OpenRouter",
            icon = OpenRouterIcon,
            baseUrl = "https://openrouter.ai/api",
            requestPath = "v1/chat/completions",
            models =
                modelsOf(
                    OPENROUTER_ID,
                    "openrouter/auto" to "Auto (best available)",
                    "anthropic/claude-sonnet-5" to "Claude Sonnet 5",
                    "openai/gpt-5.4" to "GPT-5.4",
                    "google/gemini-4-hydrogen" to "Gemini 4 Hydrogen",
                    "deepseek/deepseek-v4-pro" to "DeepSeek V4 Pro",
                    "x-ai/grok-4.7" to "Grok 4.7",
                    "moonshotai/kimi-k3" to "Kimi K3",
                    "z-ai/glm-5" to "GLM-5",
                ),
        )

    val mistral: AiProvider =
        chatProvider(
            id = MISTRAL_ID,
            displayName = "Mistral AI",
            icon = MistralIcon,
            baseUrl = "https://api.mistral.ai",
            requestPath = "v1/chat/completions",
            models =
                modelsOf(
                    MISTRAL_ID,
                    "mistral-large-latest" to "Mistral Large",
                    "mistral-medium-latest" to "Mistral Medium",
                    "mistral-small-latest" to "Mistral Small",
                    "codestral-latest" to "Codestral",
                ),
        )

    val perplexity: AiProvider =
        chatProvider(
            id = PERPLEXITY_ID,
            displayName = "Perplexity",
            icon = PerplexityIcon,
            baseUrl = "https://api.perplexity.ai",
            requestPath = "chat/completions",
            models =
                modelsOf(
                    PERPLEXITY_ID,
                    "sonar" to "Sonar",
                    "sonar-pro" to "Sonar Pro",
                    "sonar-reasoning-pro" to "Sonar Reasoning Pro",
                ),
        )

    /** Runs on the device (for example inside the built-in terminal) or on your LAN. */
    val ollama: AiProvider =
        chatProvider(
            id = OLLAMA_ID,
            displayName = "Ollama",
            icon = OllamaIcon,
            baseUrl = "http://127.0.0.1:11434",
            requestPath = "v1/chat/completions",
            requiresApiKey = false,
            models =
                modelsOf(
                    OLLAMA_ID,
                    "qwen3-coder" to "Qwen3 Coder",
                    "deepseek-r1" to "DeepSeek R1",
                    "llama3.2" to "Llama 3.2",
                    "gemma3" to "Gemma 3",
                ),
        )

    /** Accepts whichever model the desktop app has loaded; type its id in the model picker. */
    val lmStudio: AiProvider =
        chatProvider(
            id = LM_STUDIO_ID,
            displayName = "LM Studio",
            icon = LmStudioIcon,
            baseUrl = "http://127.0.0.1:1234",
            requestPath = "v1/chat/completions",
            requiresApiKey = false,
            models = modelsOf(LM_STUDIO_ID, "local-model" to "Loaded model"),
        )

    fun all(): List<AiProvider> =
        listOf(
            deepSeek,
            openAi,
            anthropic,
            gemini,
            openRouter,
            mistral,
            perplexity,
            ollama,
            lmStudio,
        )

    fun default(): AiProvider = deepSeek

    private fun chatProvider(
        id: String,
        displayName: String,
        icon: ImageVector,
        baseUrl: String,
        requestPath: String,
        models: List<AiModel>,
        requiresApiKey: Boolean = true,
    ): AiProvider =
        OpenAiCompatibleProvider(
            id = id,
            displayName = displayName,
            icon = icon,
            baseUrl = baseUrl,
            requestPath = requestPath,
            models = models,
            requiresApiKey = requiresApiKey,
        )

    private fun modelsOf(providerId: String, vararg entries: Pair<String, String>): List<AiModel> =
        entries.map { (modelId, displayName) ->
            AiModel(id = modelId, providerId = providerId, displayName = displayName)
        }

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

/** Speaks the OpenAI chat-completions protocol; pass [executorFactory] to swap the client. */
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
