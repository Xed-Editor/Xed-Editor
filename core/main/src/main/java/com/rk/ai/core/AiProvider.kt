package com.rk.ai.core

import kotlinx.coroutines.flow.Flow

data class AiRequest(
    val prompt: String,
    val systemPrompt: String = "",
    val model: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val stream: Boolean = true,
    val conversationHistory: List<ChatMessage> = emptyList(),
)

data class AiResponse(
    val content: String,
    val model: String = "",
    val providerId: String = "",
    val usage: TokenUsage? = null,
    val finishReason: String = "",
    val traceId: String = "",
)

data class AiChunk(
    val content: String,
    val done: Boolean = false,
    val finishReason: String = "",
    val usage: TokenUsage? = null,
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)

data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

data class ModelInfo(
    val id: String,
    val name: String,
    val providerId: String,
    val supportsStreaming: Boolean = true,
    val contextLength: Int = 0,
)

interface AiProvider {
    val id: String
    val displayName: String

    /** Execute a completion and return the full response. */
    suspend fun complete(request: AiRequest): Result<AiResponse>

    /** Stream a completion, emitting chunks as they arrive. */
    suspend fun stream(request: AiRequest): Flow<AiChunk>

    /** List available models for this provider. */
    suspend fun listModels(): Result<List<ModelInfo>>

    /** Check if the provider is currently reachable and authenticated. */
    suspend fun health(): ProviderHealth

    /** Whether this provider supports streaming responses. */
    val supportsStreaming: Boolean get() = true

    /** Whether this provider requires an API key. */
    val requiresApiKey: Boolean get() = true
}

data class ProviderHealth(
    val healthy: Boolean,
    val latencyMs: Long = 0,
    val message: String = "",
)
