package com.rk.ai.provider

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import android.util.Log
import com.rk.ai.settings.AiSettings

object AiProviderRuntime {
    private var cachedKey: String? = null
    private var cachedExecutor: PromptExecutor? = null

    fun activeProvider(): AiProvider = AiProviderRegistry.resolveActive(AiSettings.providerId)

    fun baseUrl(): String = activeProvider().baseUrl

    fun requestPath(): String = activeProvider().requestPath

    /** False when the active provider needs a key the user has not entered. */
    fun hasApiKey(): Boolean {
        val provider = activeProvider()
        return !provider.requiresApiKey || AiSettings.apiKey(provider.id).isNotBlank()
    }

    /** The configured model, or [modelId] when one is given. */
    fun model(modelId: String? = null): LLModel {
        val provider = activeProvider()
        val configured = resolveModelId(provider, modelId)
        val descriptor =
            AiModelCatalog.find(provider.id, configured) ?: AiModel(id = configured, providerId = provider.id)
        return descriptor.toLLModel(provider.llmProvider)
    }

    @Synchronized
    fun executor(modelId: String? = null): PromptExecutor {
        val provider = activeProvider()
        val baseUrl = provider.baseUrl
        val path = provider.requestPath
        val apiKey = AiSettings.apiKey(provider.id)
        val resolved = resolveModelId(provider, modelId)
        val key = "${provider.id}|$apiKey|$baseUrl|$path|$resolved"

        cachedExecutor?.let { existing ->
            if (cachedKey == key) return existing
        }

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
            ?: AiSettings.modelId.ifBlank { AiModelCatalog.defaultFor(provider)?.id.orEmpty() }
}

private const val TAG = "XedAI"
