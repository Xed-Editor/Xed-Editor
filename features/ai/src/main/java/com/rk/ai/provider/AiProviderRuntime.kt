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

    fun model(): LLModel {
        val provider = activeProvider()
        val configured = configuredModelId(provider)
        val descriptor =
            AiModelCatalog.find(provider.id, configured) ?: AiModel(id = configured, providerId = provider.id)
        return descriptor.toLLModel(provider.llmProvider)
    }

    @Synchronized
    fun executor(): PromptExecutor {
        val provider = activeProvider()
        val baseUrl = provider.baseUrl
        val path = provider.requestPath
        val apiKey = AiSettings.apiKey(provider.id)
        val modelId = configuredModelId(provider)
        val key = "${provider.id}|$apiKey|$baseUrl|$path|$modelId"

        cachedExecutor?.let { existing ->
            if (cachedKey == key) return existing
        }

        runCatching { cachedExecutor?.close() }
        Log.i(TAG, "Building executor: provider=${provider.id} baseUrl=$baseUrl path=$path model=$modelId")

        val executor = provider.createExecutor(AiProviderConfig(apiKey, baseUrl, path, modelId))
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

    private fun configuredModelId(provider: AiProvider): String =
        AiSettings.modelId.ifBlank { AiModelCatalog.defaultFor(provider)?.id.orEmpty() }
}

private const val TAG = "XedAI"
