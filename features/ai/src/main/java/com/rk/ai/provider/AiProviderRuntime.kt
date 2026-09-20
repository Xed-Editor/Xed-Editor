package com.rk.ai.provider

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import android.util.Log
import com.rk.ai.settings.AiSettings

object AiProviderRuntime {
    private var cachedKey: String? = null
    private var cachedExecutor: PromptExecutor? = null

    fun activeProvider(): AiProvider =
        AiProviderRegistry.resolveActive(AiSettings.providerId, AiSettings.baseUrl)
            ?: BuiltinProviders.openAiCompatible

    fun baseUrl(): String = AiSettings.baseUrl.ifBlank { activeProvider().defaultBaseUrl }

    fun chatCompletionsPath(): String = activeProvider().resolveChatCompletionsPath(baseUrl())

    fun model(): LLModel {
        val provider = activeProvider()
        val configured = AiSettings.modelId.ifBlank { AiModelCatalog.defaultFor(provider)?.id.orEmpty() }
        val descriptor =
            AiModelCatalog.find(provider.id, configured) ?: AiModel(id = configured, providerId = provider.id)
        return descriptor.toLLModel(provider.llmProvider)
    }

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

    @Synchronized
    fun invalidate() {
        runCatching { cachedExecutor?.close() }
        cachedExecutor = null
        cachedKey = null
    }
}

private const val TAG = "XedAI"
