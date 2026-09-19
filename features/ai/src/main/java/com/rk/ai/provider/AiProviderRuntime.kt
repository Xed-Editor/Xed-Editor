package com.rk.ai.provider

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import android.util.Log
import com.rk.ai.settings.AiSettings

/**
 * Resolves the provider, model and executor that the current settings describe, and caches the
 * executor until one of those inputs changes.
 *
 * Keeping this here - rather than inside [AiSettings] - means settings stay a plain preference bag
 * and providers stay independent of where the values are stored.
 */
object AiProviderRuntime {
    private var cachedKey: String? = null
    private var cachedExecutor: PromptExecutor? = null

    /** The provider a new run will use. */
    fun activeProvider(): AiProvider =
        AiProviderRegistry.resolveActive(AiSettings.providerId, AiSettings.baseUrl)
            ?: BuiltinProviders.openAiCompatible

    /** The endpoint root actually used, defaulting an empty setting to the provider's own. */
    fun baseUrl(): String = AiSettings.baseUrl.ifBlank { activeProvider().defaultBaseUrl }

    /** The chat-completions path for the active provider and base URL. */
    fun chatCompletionsPath(): String = activeProvider().resolveChatCompletionsPath(baseUrl())

    /** The model descriptor for the configured model id, falling back to the provider's first model. */
    fun model(): LLModel {
        val provider = activeProvider()
        val configured = AiSettings.modelId.ifBlank { AiModelCatalog.defaultFor(provider)?.id.orEmpty() }
        val descriptor =
            AiModelCatalog.find(provider.id, configured) ?: AiModel(id = configured, providerId = provider.id)
        return descriptor.toLLModel(provider.llmProvider)
    }

    /** The cached executor for the current settings, rebuilt whenever an input changes. */
    @Synchronized
    fun executor(): PromptExecutor {
        val provider = activeProvider()
        val baseUrl = baseUrl()
        val path = provider.resolveChatCompletionsPath(baseUrl)
        val key = "${provider.id}|${AiSettings.apiKey}|$baseUrl|$path"

        cachedExecutor?.let { existing ->
            if (cachedKey == key) return existing
        }

        runCatching { cachedExecutor?.close() }
        Log.i(
            TAG,
            "Building executor: provider=${provider.id} baseUrl=$baseUrl path=$path model=${AiSettings.modelId}",
        )

        val executor = provider.createExecutor(AiProviderConfig(AiSettings.apiKey, baseUrl, path))
        cachedKey = key
        cachedExecutor = executor
        return executor
    }

    /** Drops the cached executor so the next run rebuilds it. */
    @Synchronized
    fun invalidate() {
        runCatching { cachedExecutor?.close() }
        cachedExecutor = null
        cachedKey = null
    }
}

private const val TAG = "XedAI"
