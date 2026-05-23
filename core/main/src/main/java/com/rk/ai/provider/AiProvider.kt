package com.rk.ai.provider

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface AiProvider {
    val name: String
    val displayName: String
    val supportsStreaming: Boolean get() = true
    val supportsFunctions: Boolean get() = true

    suspend fun complete(request: CompletionRequest): CompletionResponse
    suspend fun streamComplete(request: CompletionRequest): Flow<StreamEvent>
    suspend fun cancel(requestId: String)
}

enum class ProviderType(val key: String) {
    OPENAI("openai"),
    GEMINI("gemini"),
    CLAUDE("claude"),
    OPENCODE("opencode"),
    CUSTOM("custom");

    companion object {
        fun fromKey(key: String): ProviderType =
            entries.find { it.key == key.lowercase() } ?: CUSTOM
    }
}

data class ProviderConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val providerType: ProviderType = ProviderType.OPENAI,
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val additionalHeaders: Map<String, String> = emptyMap(),
)

data class Message(
    val role: MessageRole,
    val content: String,
    val name: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
)

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolFunction,
)

data class ToolFunction(
    val name: String,
    val arguments: String,
)

data class CompletionRequest(
    val messages: List<Message>,
    val config: ProviderConfig,
    val systemPrompt: String = "",
    val tools: List<JsonSchemaTool> = emptyList(),
    val stopSequences: List<String> = emptyList(),
    val cacheKey: String? = null,
)

data class JsonSchemaTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any> = emptyMap(),
)

data class CompletionResponse(
    val id: String,
    val content: String,
    val finishReason: FinishReason = FinishReason.STOP,
    val usage: Usage? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val providerName: String = "",
)

data class Usage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)

enum class FinishReason { STOP, LENGTH, TOOL_CALLS, CONTENT_FILTER, ERROR }

sealed class StreamEvent {
    data class Delta(val content: String) : StreamEvent()
    data class ToolCallDelta(val id: String, val name: String, val arguments: String) : StreamEvent()
    data class Done(val response: CompletionResponse) : StreamEvent()
    data class Error(val exception: Throwable) : StreamEvent()
    data object Complete : StreamEvent()
}

data class ActiveRequest(
    val id: String,
    val job: Job,
    val provider: String,
    val startedAt: Long = System.currentTimeMillis(),
)
