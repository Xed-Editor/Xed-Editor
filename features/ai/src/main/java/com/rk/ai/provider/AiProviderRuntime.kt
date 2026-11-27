package com.rk.ai.provider

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import android.util.Log
import com.rk.ai.settings.AiSettings

object AiProviderRuntime {
    private var cachedKey: String? = null
    private var cachedExecutor: PromptExecutor? = null

    fun activeProvider(): AiProvider = AiProviderRegistry.resolveActive(AiSettings.providerId)

    fun baseUrl(): String = normalizeBaseUrl(activeProvider().baseUrl)

    fun requestPath(): String = normalizeRequestPath(activeProvider().requestPath)

    /** False when the active provider needs a key the user has not entered. */
    fun hasApiKey(): Boolean {
        val provider = activeProvider()
        return !provider.requiresApiKey || AiSettings.apiKey(provider.id).isNotBlank()
    }

    /** The model selected in settings, or [modelId] when one is given. */
    fun model(modelId: String? = null): LLModel {
        val provider = activeProvider()
        val configured = resolveModelId(provider, modelId)
        val descriptor =
            AiModelCatalog.find(provider.id, configured) ?: AiModel(id = configured, providerId = provider.id)
        return descriptor.toLLModel(provider.llmProvider)
    }

    /** The model id [executor] will send, as shown in the chat header. */
    fun modelId(modelId: String? = null): String = resolveModelId(activeProvider(), modelId)

    @Synchronized
    fun executor(modelId: String? = null): PromptExecutor {
        val provider = activeProvider()
        val baseUrl = normalizeBaseUrl(provider.baseUrl)
        val path = normalizeRequestPath(provider.requestPath)
        val apiKey = AiSettings.apiKey(provider.id)
        val resolved = resolveModelId(provider, modelId)
        val key = "${provider.id}|$apiKey|$baseUrl|$path|$resolved"

        cachedExecutor?.let { existing -> if (cachedKey == key) return existing }

        runCatching { cachedExecutor?.close() }
        Log.i(TAG, "Building executor: provider=${provider.id} baseUrl=$baseUrl path=$path model=$resolved")

        val executor = provider.createExecutor(AiProviderConfig(apiKey, baseUrl, path, resolved))
        cachedKey = key
        cachedExecutor = executor
        return executor
    }

    @Synchronized
    fun invalidate() {
        runCatching { cachedExecutor?.close() }
        cachedExecutor = null
        cachedKey = null
    }

    private fun resolveModelId(provider: AiProvider, requested: String?): String =
        requested?.takeIf { it.isNotBlank() }
            ?: AiSettings.modelId(provider.id).ifBlank { AiModelCatalog.defaultFor(provider)?.id.orEmpty() }

    /**
     * The HTTP client joins [baseUrl] and the request path with a single `/`, so a trailing slash on either half — easy
     * to type into a custom provider — would produce a double slash.
     */
    private fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')

    private fun normalizeRequestPath(path: String): String = path.trim().trimStart('/')
}

private const val TAG = "XedAI"
