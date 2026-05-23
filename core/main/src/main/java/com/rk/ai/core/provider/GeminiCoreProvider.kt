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
import com.rk.ai.core.ModelInfo
import com.rk.ai.core.ProviderHealth
import com.rk.ai.core.TokenUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

class GeminiCoreProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    override val id: String = "gemini",
    override val displayName: String = "Google Gemini",
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
        val jsonBody = buildGeminiRequest(request)
        val url = buildUrl(request.model, "generateContent")
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(httpRequest).execute()
        parseGenerateResponse(response, request.model)
    }

    override suspend fun stream(request: AiRequest): Flow<AiChunk> = callbackFlow {
        val producer = this
        val requestId = "gemini-${System.currentTimeMillis()}-${request.hashCode()}"
        val cancelled = AtomicBoolean(false)
        cancelledTokens[requestId] = cancelled

        try {
            val jsonBody = buildGeminiRequest(request)
            val url = buildUrl(request.model, "streamGenerateContent?alt=sse")
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            EventSources.createFactory(client).newEventSource(httpRequest, object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (cancelled.get()) return
                    try {
                        val json = JsonParser.parseString(data).asJsonObject
                        val candidates = json.getAsJsonArray("candidates")
                        if (candidates == null || candidates.isEmpty()) return
                        val content = candidates[0].asJsonObject.getAsJsonObject("content")
                        val parts = content?.getAsJsonArray("parts") ?: return
                        parts.forEach { part ->
                            val text = part.asJsonObject.get("text")?.asString
                            if (text != null && text.isNotBlank()) {
                                producer.trySend(AiChunk(content = text))
                            }
                        }
                        val finishReason = candidates[0].asJsonObject.get("finishReason")?.asString
                        if (finishReason != null && finishReason != "FINISH_REASON_UNSPECIFIED") {
                            producer.trySend(AiChunk(content = "", done = true, finishReason = "stop"))
                            close()
                        }
                    } catch (_: Exception) {}
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    if (!cancelled.get()) {
                        producer.trySend(AiChunk(content = "", done = true, finishReason = "error"))
                    }
                    close()
                }

                override fun onClosed(eventSource: EventSource) { close() }
            })

            awaitClose {
                cancelled.set(true)
                cancelledTokens.remove(requestId)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            producer.trySend(AiChunk(content = "", done = true, finishReason = "error"))
            close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): Result<List<ModelInfo>> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/models?key=$apiKey")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw AiError.ProviderUnavailable(id, "empty response")
        if (!response.isSuccessful) throw AiError.ProviderUnavailable(id, "HTTP ${response.code}")
        val json = JsonParser.parseString(body).asJsonObject
        val models = json.getAsJsonArray("models") ?: JsonArray()
        models.map { it.asJsonObject.get("name")?.asString?.removePrefix("models/") ?: "" }
            .filter { it.isNotBlank() }
            .map { ModelInfo(id = it, name = it, providerId = id, supportsStreaming = it.contains("flash") || it.contains("pro")) }
    }

    override suspend fun health(): ProviderHealth {
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url("$baseUrl/models?key=$apiKey")
                .head()
                .build()
            val response = client.newCall(request).execute()
            ProviderHealth(
                healthy = response.isSuccessful,
                latencyMs = System.currentTimeMillis() - start,
                message = if (response.isSuccessful) "OK" else "HTTP ${response.code}"
            )
        } catch (e: Exception) {
            ProviderHealth(healthy = false, message = e.message ?: "unknown")
        }
    }

    private fun buildGeminiRequest(request: AiRequest): String {
        val model = request.model.ifBlank { "gemini-2.5-flash" }
        val body = JsonObject().apply {
            add("contents", JsonArray().apply {
                if (request.systemPrompt.isNotBlank()) {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", request.systemPrompt) })
                        })
                    })
                }
                request.conversationHistory.forEach { msg ->
                    if (msg.content.isNotBlank()) {
                        add(JsonObject().apply {
                            addProperty("role", if (msg.role == "assistant") "model" else "user")
                            add("parts", JsonArray().apply {
                                add(JsonObject().apply { addProperty("text", msg.content) })
                            })
                        })
                    }
                }
                if (request.prompt.isNotBlank()) {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", request.prompt) })
                        })
                    })
                }
            })
            add("generationConfig", JsonObject().apply {
                addProperty("maxOutputTokens", request.maxTokens)
                addProperty("temperature", request.temperature)
            })
        }
        return gson.toJson(body)
    }

    private fun buildUrl(model: String, endpoint: String): String {
        val effectiveModel = model.ifBlank { "gemini-2.5-flash" }
        return "$baseUrl/models/$effectiveModel:$endpoint?key=$apiKey"
    }

    private fun parseGenerateResponse(response: okhttp3.Response, model: String): AiResponse {
        val body = response.body?.string() ?: throw AiError.ProviderUnavailable(id, "empty response")
        if (!response.isSuccessful) {
            throw AiError.ProviderUnavailable(id, "HTTP ${response.code}: ${body.take(200)}")
        }
        val json = JsonParser.parseString(body).asJsonObject
        val text = json.getAsJsonArray("candidates")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString
            ?: ""
        val usageMetadata = json.getAsJsonObject("usageMetadata")
        return AiResponse(
            content = text.trim(),
            model = model,
            providerId = id,
            usage = usageMetadata?.let {
                TokenUsage(
                    promptTokens = it.get("promptTokenCount")?.asInt ?: 0,
                    completionTokens = it.get("candidatesTokenCount")?.asInt ?: 0,
                    totalTokens = it.get("totalTokenCount")?.asInt ?: 0,
                )
            },
        )
    }
}
