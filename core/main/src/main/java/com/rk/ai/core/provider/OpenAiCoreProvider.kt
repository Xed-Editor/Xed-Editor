package com.rk.ai.core.provider

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.core.AiChunk
import com.rk.ai.core.AiError
import com.rk.ai.core.AiProvider
import com.rk.ai.core.AiRequest
import com.rk.ai.core.AiResponse
import com.rk.ai.core.ChatMessage
import com.rk.ai.core.ModelInfo
import com.rk.ai.core.ProviderHealth
import com.rk.ai.core.TokenUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OpenAiCoreProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    override val id: String = "openai",
    override val displayName: String = "OpenAI Compatible",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) : AiProvider {

    private val gson = Gson()
    private val cancelledTokens = ConcurrentHashMap<String, AtomicBoolean>()

    override val supportsStreaming: Boolean = true
    override val requiresApiKey: Boolean = true

    override suspend fun complete(request: AiRequest): Result<AiResponse> = runCatching {
        val jsonBody = buildChatRequest(request, stream = false)
        val httpRequest = buildRequest(jsonBody)
        val response = client.newCall(httpRequest).execute()
        parseResponse(response, request.model)
    }.mapError()

    override suspend fun stream(request: AiRequest): Flow<AiChunk> = flow {
        val collector = this
        val requestId = "openai-${System.currentTimeMillis()}-${request.hashCode()}"
        val cancelled = AtomicBoolean(false)
        cancelledTokens[requestId] = cancelled

        try {
            val jsonBody = buildChatRequest(request, stream = true)
            val httpRequest = buildRequest(jsonBody)

            var responseId = ""
            val collectedContent = StringBuilder()
            val done = AtomicBoolean(false)

            val sseRequest = Request.Builder()
                .url(httpRequest.url)
                .headers(httpRequest.headers)
                .apply { httpRequest.body?.let { post(it) } }
                .build()

            EventSources.createFactory(client).newEventSource(sseRequest, object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (cancelled.get() || done.get()) return
                    if (data == "[DONE]") { done.set(true); return }
                    try {
                        val json = JsonParser.parseString(data).asJsonObject
                        val choices = json.getAsJsonArray("choices")
                        if (choices == null || choices.isEmpty()) return
                        val delta = choices[0].asJsonObject.getAsJsonObject("delta") ?: return
                        if (responseId.isBlank()) responseId = json.get("id")?.asString ?: ""
                        val content = delta.get("content")?.asString
                        if (content != null && content.isNotBlank()) {
                            collectedContent.append(content)
                            collector.tryEmit(AiChunk(content = content))
                        }
                        val finishReason = choices[0].asJsonObject.get("finish_reason")?.asString
                        if (finishReason != null && finishReason != "null") done.set(true)
                    } catch (_: Exception) {}
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    if (!cancelled.get()) collector.tryEmit(AiChunk(content = "", done = true, finishReason = "error"))
                    done.set(true)
                }

                override fun onClosed(eventSource: EventSource) { done.set(true) }
            })

            while (!done.get() && !cancelled.get()) {
                kotlinx.coroutines.delay(100)
            }

            if (!cancelled.get()) {
                emit(AiChunk(content = "", done = true, finishReason = "stop"))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (!cancelled.get()) emit(AiChunk(content = "", done = true, finishReason = "error"))
        } finally {
            cancelledTokens.remove(requestId)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): Result<List<ModelInfo>> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw AiError.ProviderUnavailable(id, "empty response")
        if (!response.isSuccessful) throw AiError.ProviderUnavailable(id, "HTTP ${response.code}")
        val json = JsonParser.parseString(body).asJsonObject
        val data = json.getAsJsonArray("data") ?: JsonArray()
        data.map { it.asJsonObject.get("id")?.asString ?: "" }
            .filter { it.isNotBlank() }
            .map { ModelInfo(id = it, name = it, providerId = id, supportsStreaming = true) }
    }.mapError()

    override suspend fun health(): ProviderHealth {
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .head()
                .build()
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            ProviderHealth(
                healthy = response.isSuccessful,
                latencyMs = latency,
                message = if (response.isSuccessful) "OK" else "HTTP ${response.code}"
            )
        } catch (e: Exception) {
            ProviderHealth(healthy = false, message = e.message ?: "unknown")
        }
    }

    private fun buildChatRequest(request: AiRequest, stream: Boolean): String {
        val body = JsonObject().apply {
            addProperty("model", request.model.ifBlank { "gpt-4o-mini" })
            addProperty("stream", stream)
            if (request.maxTokens > 0) addProperty("max_tokens", request.maxTokens)
            addProperty("temperature", request.temperature)

            val messages = JsonArray()
            if (request.systemPrompt.isNotBlank()) {
                messages.add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", request.systemPrompt)
                })
            }
            request.conversationHistory.forEach { msg ->
                messages.add(JsonObject().apply {
                    addProperty("role", msg.role)
                    addProperty("content", msg.content)
                })
            }
            messages.add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", request.prompt)
            })
            add("messages", messages)
        }
        return gson.toJson(body)
    }

    private fun buildRequest(jsonBody: String): Request {
        val url = if (baseUrl.contains("/chat/completions")) baseUrl
            else "${baseUrl.trimEnd('/')}/chat/completions"
        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun parseResponse(response: okhttp3.Response, model: String): AiResponse {
        val body = response.body?.string() ?: throw AiError.ProviderUnavailable(id, "empty response")
        if (!response.isSuccessful) {
            throw AiError.ProviderUnavailable(id, "HTTP ${response.code}: ${body.take(200)}")
        }
        val json = JsonParser.parseString(body).asJsonObject
        val content = StringBuilder()
        json.getAsJsonArray("choices")?.forEach { choice ->
            val msg = choice.asJsonObject.getAsJsonObject("message")
            msg?.get("content")?.asString?.let { content.append(it) }
        }
        val usageJson = json.getAsJsonObject("usage")
        return AiResponse(
            content = content.toString(),
            model = model,
            providerId = id,
            usage = usageJson?.let {
                TokenUsage(
                    promptTokens = it.get("prompt_tokens")?.asInt ?: 0,
                    completionTokens = it.get("completion_tokens")?.asInt ?: 0,
                    totalTokens = it.get("total_tokens")?.asInt ?: 0,
                )
            },
        )
    }
}

private fun <T> Result<T>.mapError(): Result<T> = this
