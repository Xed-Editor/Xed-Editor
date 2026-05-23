package com.rk.ai.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

object AiCoreEngine {
    private const val TAG = "AiCoreEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val providerManager: ProviderManager = ProviderManager()
    val modelCache: ModelCacheService = ModelCacheService()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        scope.launch {
            providerManager.registerDefaults()
            Log.d(TAG, "AI Core Engine initialized with ${providerManager.getProviderIds().size} providers")
        }
    }

    suspend fun resolveProvider(providerId: String? = null): AiProvider? {
        return providerManager.getProvider(providerId)
    }

    suspend fun complete(
        prompt: String,
        systemPrompt: String = "",
        model: String = "",
        providerId: String? = null,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        conversationHistory: List<ChatMessage> = emptyList(),
    ): Result<AiResponse> {
        val provider = providerManager.getProviderOrThrow(providerId)
        val request = AiRequest(
            prompt = prompt,
            systemPrompt = systemPrompt,
            model = model,
            temperature = temperature,
            maxTokens = maxTokens,
            conversationHistory = conversationHistory,
        )
        return provider.complete(request)
    }

    suspend fun stream(
        prompt: String,
        systemPrompt: String = "",
        model: String = "",
        providerId: String? = null,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        conversationHistory: List<ChatMessage> = emptyList(),
    ): Flow<AiChunk> {
        val provider = providerManager.getProvider(providerId) ?: return emptyFlow()
        val request = AiRequest(
            prompt = prompt,
            systemPrompt = systemPrompt,
            model = model,
            temperature = temperature,
            maxTokens = maxTokens,
            conversationHistory = conversationHistory,
        )
        return provider.stream(request)
    }

    suspend fun health(): Map<String, ProviderHealth> = providerManager.health()

    suspend fun listModels(providerId: String? = null): List<ModelInfo> {
        val provider = providerManager.getProvider(providerId) ?: return emptyList()
        val cached = modelCache.get(provider.id)
        if (cached != null) return cached
        val models = provider.listModels().getOrNull() ?: emptyList()
        modelCache.put(provider.id, models)
        return models
    }

    fun shutdown() {
        initialized = false
        modelCache.invalidateAll()
        Log.d(TAG, "AI Core Engine shut down")
    }
}
