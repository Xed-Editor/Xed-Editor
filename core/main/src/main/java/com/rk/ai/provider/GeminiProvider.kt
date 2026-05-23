package com.rk.ai.provider

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) : AiProvider {

    override val name: String = "gemini"
    override val displayName: String = "Google Gemini"

    private val gson = Gson()
    private val cancelledRequests = ConcurrentHashMap<String, AtomicBoolean>()

    override suspend fun complete(request: CompletionRequest): CompletionResponse =
        withContext(Dispatchers.IO) {
            val jsonBody = buildGeminiRequest(request)
            val url = buildUrl(request.config, "generateContent")
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(httpRequest).execute()
            parseGenerateResponse(response, request.config.model)
        }

    override suspend fun streamComplete(request: CompletionRequest): Flow<StreamEvent> = flow {
        val cancelled = AtomicBoolean(false)
        val requestKey = "gemini-${System.currentTimeMillis()}"
        cancelledRequests[requestKey] = cancelled

        try {
            val jsonBody = buildGeminiRequest(request)
            val url = buildUrl(request.config, "streamGenerateContent?alt=sse")
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val collectedContent = StringBuilder()
            val done = AtomicBoolean(false)

            withContext(Dispatchers.IO) {
                val listener = object : EventSourceListener() {
                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        if (cancelled.get() || done.get()) return
                        try {
                            val json = JsonParser.parseString(data).asJsonObject
                            val candidates = json.getAsJsonArray("candidates")
                            if (candidates == null || candidates.isEmpty()) return

                            val content = candidates[0].asJsonObject
                                .getAsJsonObject("content")
                            val parts = content?.getAsJsonArray("parts") ?: return
                            parts.forEach { part ->
                                val text = part.asJsonObject.get("text")?.asString
                                if (text != null && text.isNotBlank()) {
                                    collectedContent.append(text)
                                    trySend(StreamEvent.Delta(text))
                                }
                            }

                            val finishReason = candidates[0].asJsonObject
                                .get("finishReason")?.asString
                            if (finishReason != null && finishReason != "FINISH_REASON_UNSPECIFIED") {
                                done.set(true)
                            }
                        } catch (e: Exception) {
                            // Skip malformed events
                        }
                    }

                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                        if (!cancelled.get()) trySend(StreamEvent.Error(t ?: Exception("SSE failed")))
                        done.set(true)
                    }

                    override fun onClosed(eventSource: EventSource) {
                        done.set(true)
                    }
                }

                EventSources.createFactory(client).newEventSource(httpRequest, listener)

                while (!done.get() && !cancelled.get() && coroutineContext.isActive) {
                    kotlinx.coroutines.delay(100)
                }
            }

            if (!cancelled.get()) {
                emit(
                    StreamEvent.Done(
                        CompletionResponse(
                            id = "gemini-${System.currentTimeMillis()}",
                            content = collectedContent.toString(),
                            finishReason = FinishReason.STOP,
                            providerName = name,
                        )
                    )
                )
            }
        } catch (e: Exception) {
            if (!cancelled.get()) emit(StreamEvent.Error(e))
        } finally {
            cancelledRequests.remove(requestKey)
            emit(StreamEvent.Complete)
        }
    }

    override suspend fun cancel(requestId: String) {
        cancelledRequests[requestId]?.set(true)
    }

    private fun buildGeminiRequest(request: CompletionRequest): String {
        val body = JsonObject().apply {
            add("contents", JsonArray().apply {
                if (request.systemPrompt.isNotBlank()) {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", request.systemPrompt) })
                            request.messages.forEach { msg ->
                                if (msg.content.isNotBlank()) {
                                    add(JsonObject().apply { addProperty("text", "${msg.role.name}: ${msg.content}") })
                                }
                            }
                        })
                    })
                } else {
                    request.messages.forEach { msg ->
                        if (msg.content.isNotBlank()) {
                            val role = when (msg.role) {
                                MessageRole.ASSISTANT -> "model"
                                else -> "user"
                            }
                            add(JsonObject().apply {
                                addProperty("role", role)
                                add("parts", JsonArray().apply {
                                    add(JsonObject().apply { addProperty("text", msg.content) })
                                })
                            })
                        }
                    }
                }
            })

            val genConfig = JsonObject()
            genConfig.addProperty("maxOutputTokens", request.config.maxTokens)
            genConfig.addProperty("temperature", request.config.temperature)
            genConfig.addProperty("topP", request.config.topP)
            if (request.stopSequences.isNotEmpty()) {
                val stops = JsonArray()
                request.stopSequences.forEach { stops.add(it) }
                genConfig.add("stopSequences", stops)
            }
            add("generationConfig", genConfig)

            if (request.tools.isNotEmpty()) {
                add("tools", JsonArray().apply {
                    add(JsonObject().apply {
                        add("functionDeclarations", JsonArray().apply {
                            request.tools.forEach { tool ->
                                add(JsonObject().apply {
                                    addProperty("name", tool.name)
                                    addProperty("description", tool.description)
                                    add("parameters", gson.toJsonTree(tool.inputSchema))
                                })
                            }
                        })
                    })
                })
            }
        }
        return gson.toJson(body)
    }

    private fun buildUrl(config: ProviderConfig, endpoint: String): String {
        val base = config.baseUrl.ifBlank {
            "https://generativelanguage.googleapis.com/v1beta/models/${config.model}"
        }
        val apiKey = config.apiKey.ifBlank {
            System.getenv("GEMINI_API_KEY") ?: System.getenv("GOOGLE_API_KEY") ?: ""
        }
        val separator = if (base.contains("?")) "&" else "?"
        return "$base:$endpoint$separator key=$apiKey"
    }

    private fun parseGenerateResponse(response: Response, model: String): CompletionResponse {
        val body = response.body?.string() ?: throw Exception("Empty response")
        if (!response.isSuccessful) {
            throw Exception("Gemini API error ${response.code}: ${body.take(200)}")
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
        val usage = if (usageMetadata != null) {
            Usage(
                promptTokens = usageMetadata.get("promptTokenCount")?.asInt ?: 0,
                completionTokens = usageMetadata.get("candidatesTokenCount")?.asInt ?: 0,
                totalTokens = usageMetadata.get("totalTokenCount")?.asInt ?: 0,
            )
        } else null

        return CompletionResponse(
            id = "gemini-${System.currentTimeMillis()}",
            content = text.trim(),
            finishReason = FinishReason.STOP,
            usage = usage,
            providerName = name,
        )
    }
}
