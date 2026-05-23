package com.rk.ai.provider

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
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

class OpenAiProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) : AiProvider {

    override val name: String = "openai"
    override val displayName: String = "OpenAI Compatible"

    private val gson = Gson()
    private val activeRequests = ConcurrentHashMap<String, ActiveRequest>()
    private val cancelledTokens = ConcurrentHashMap<String, AtomicBoolean>()

    override suspend fun complete(request: CompletionRequest): CompletionResponse =
        withContext(Dispatchers.IO) {
            val jsonBody = buildChatRequest(request)
            val httpRequest = buildHttpRequest(request.config, jsonBody)
            val response = client.newCall(httpRequest).execute()
            parseResponse(request.config.providerType, response, request.config.model)
        }

    override suspend fun streamComplete(request: CompletionRequest): Flow<StreamEvent> = flow {
        val requestId = "openai-${System.currentTimeMillis()}-${request.messages.hashCode()}"
        val cancelled = AtomicBoolean(false)
        cancelledTokens[requestId] = cancelled

        try {
            val jsonBody = buildChatRequest(request, stream = true)
            val httpRequest = buildHttpRequest(request.config, jsonBody)

            val sseRequest = Request.Builder()
                .url(httpRequest.url)
                .headers(httpRequest.headers)
                .apply { httpRequest.body?.let { post(it) } }
                .build()

            val collectedContent = StringBuilder()
            val collectedToolCalls = mutableMapOf<String, ToolCall.Builder>()
            var responseId = ""

            withContext(Dispatchers.IO) {
                val done = AtomicBoolean(false)

                val listener = object : EventSourceListener() {
                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        if (cancelled.get() || done.get()) return
                        if (data == "[DONE]") {
                            done.set(true)
                            return
                        }
                        try {
                            val json = JsonParser.parseString(data).asJsonObject
                            val choices = json.getAsJsonArray("choices")
                            if (choices == null || choices.isEmpty()) return

                            val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                            if (delta == null) return

                            if (responseId.isBlank()) {
                                responseId = json.get("id")?.asString ?: ""
                            }

                            val content = delta.get("content")?.asString
                            if (content != null && content.isNotBlank()) {
                                collectedContent.append(content)
                                trySend(StreamEvent.Delta(content))
                            }

                            val toolCalls = delta.getAsJsonArray("tool_calls")
                            if (toolCalls != null) {
                                toolCalls.forEach { tcElem ->
                                    val tc = tcElem.asJsonObject
                                    val index = tc.get("index")?.asInt ?: return@forEach
                                    val fn = tc.getAsJsonObject("function") ?: return@forEach
                                    val name = fn.get("name")?.asString
                                    val args = fn.get("arguments")?.asString
                                    val id = tc.get("id")?.asString

                                    val builder = collectedToolCalls.getOrPut(index.toString()) {
                                        ToolCall.Builder().apply {
                                            if (id != null) this.id = id
                                        }
                                    }
                                    if (name != null) builder.functionName.append(name)
                                    if (args != null) builder.arguments.append(args)
                                }
                            }

                            val finishReason = choices[0].asJsonObject.get("finish_reason")?.asString
                            if (finishReason != null && finishReason != "null") {
                                done.set(true)
                            }
                        } catch (e: Exception) {
                            // Skip malformed events
                        }
                    }

                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                        if (cancelled.get()) return
                        done.set(true)
                        trySend(StreamEvent.Error(t ?: Exception("SSE failed: ${response?.code}")))
                    }

                    override fun onClosed(eventSource: EventSource) {
                        done.set(true)
                    }
                }

                EventSources.createFactory(client).newEventSource(sseRequest, listener)

                while (!done.get() && !cancelled.get() && isActive) {
                    kotlinx.coroutines.delay(100)
                }
            }

            if (!cancelled.get()) {
                val toolCalls = collectedToolCalls.values.map { it.build() }
                emit(
                    StreamEvent.Done(
                        CompletionResponse(
                            id = responseId,
                            content = collectedContent.toString(),
                            finishReason = if (collectedToolCalls.isNotEmpty()) FinishReason.TOOL_CALLS else FinishReason.STOP,
                            toolCalls = toolCalls,
                            providerName = name,
                        )
                    )
                )
            }
        } catch (e: Exception) {
            if (!cancelled.get()) emit(StreamEvent.Error(e))
        } finally {
            cancelledTokens.remove(requestId)
            emit(StreamEvent.Complete)
        }
    }

    override suspend fun cancel(requestId: String) {
        cancelledTokens[requestId]?.set(true)
        activeRequests[requestId]?.let { req ->
            req.job.cancelAndJoin()
            activeRequests.remove(requestId)
        }
    }

    private fun buildChatRequest(request: CompletionRequest, stream: Boolean = false): String {
        val body = JsonObject().apply {
            addProperty("model", request.config.model)
            addProperty("stream", stream)
            if (request.config.maxTokens > 0) addProperty("max_tokens", request.config.maxTokens)
            addProperty("temperature", request.config.temperature)
            addProperty("top_p", request.config.topP)

            val messages = JsonArray()
            if (request.systemPrompt.isNotBlank()) {
                messages.add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", request.systemPrompt)
                })
            }
            request.messages.forEach { msg ->
                messages.add(buildMessageJson(msg))
            }
            add("messages", messages)

            if (request.stopSequences.isNotEmpty()) {
                val stops = JsonArray()
                request.stopSequences.forEach { stops.add(it) }
                add("stop", stops)
            }

            if (request.tools.isNotEmpty()) {
                val tools = JsonArray()
                request.tools.forEach { tool ->
                    tools.add(JsonObject().apply {
                        addProperty("type", "function")
                        add("function", JsonObject().apply {
                            addProperty("name", tool.name)
                            addProperty("description", tool.description)
                            add("parameters", gson.toJsonTree(tool.inputSchema))
                        })
                    })
                }
                add("tools", tools)
            }
        }
        return gson.toJson(body)
    }

    private fun buildMessageJson(msg: Message): JsonObject = JsonObject().apply {
        when (msg.role) {
            MessageRole.SYSTEM -> addProperty("role", "system")
            MessageRole.USER -> addProperty("role", "user")
            MessageRole.ASSISTANT -> addProperty("role", "assistant")
            MessageRole.TOOL -> {
                addProperty("role", "tool")
                msg.toolCallId?.let { addProperty("tool_call_id", it) }
                return@apply
            }
        }
        addProperty("content", msg.content)
        msg.name?.let { addProperty("name", it) }

        if (msg.toolCalls.isNotEmpty()) {
            val tcs = JsonArray()
            msg.toolCalls.forEach { tc ->
                tcs.add(JsonObject().apply {
                    addProperty("id", tc.id)
                    addProperty("type", tc.type)
                    add("function", JsonObject().apply {
                        addProperty("name", tc.function.name)
                        addProperty("arguments", tc.function.arguments)
                    })
                })
            }
            add("tool_calls", tcs)
        }
    }

    private fun buildHttpRequest(config: ProviderConfig, jsonBody: String): Request {
        val url = if (config.baseUrl.isNotBlank() && !config.baseUrl.contains("/chat/completions")) {
            "${config.baseUrl.trimEnd('/')}/chat/completions"
        } else config.baseUrl.ifBlank { "https://api.openai.com/v1/chat/completions" }

        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .apply {
                config.additionalHeaders.forEach { (k, v) -> addHeader(k, v) }
            }
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun parseResponse(providerType: ProviderType, response: Response, model: String): CompletionResponse {
        val body = response.body?.string() ?: throw Exception("Empty response body")
        if (!response.isSuccessful) {
            throw Exception("API error ${response.code}: ${body.take(200)}")
        }
        val json = JsonParser.parseString(body).asJsonObject

        val id = json.get("id")?.asString ?: ""
        val content = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()

        json.getAsJsonArray("choices")?.forEach { choice ->
            val msg = choice.asJsonObject.getAsJsonObject("message") ?: return@forEach

            msg.get("content")?.asString?.let { content.append(it) }

            msg.getAsJsonArray("tool_calls")?.forEach { tcElem ->
                val tc = tcElem.asJsonObject
                val fn = tc.getAsJsonObject("function")
                toolCalls.add(
                    ToolCall(
                        id = tc.get("id")?.asString ?: "",
                        type = tc.get("type")?.asString ?: "function",
                        function = ToolFunction(
                            name = fn?.get("name")?.asString ?: "",
                            arguments = fn?.get("arguments")?.asString ?: "",
                        )
                    )
                )
            }
        }

        val usageJson = json.getAsJsonObject("usage")
        val usage = if (usageJson != null) {
            Usage(
                promptTokens = usageJson.get("prompt_tokens")?.asInt ?: 0,
                completionTokens = usageJson.get("completion_tokens")?.asInt ?: 0,
                totalTokens = usageJson.get("total_tokens")?.asInt ?: 0,
            )
        } else null

        return CompletionResponse(
            id = id,
            content = content.toString(),
            finishReason = if (toolCalls.isNotEmpty()) FinishReason.TOOL_CALLS else FinishReason.STOP,
            usage = usage,
            toolCalls = toolCalls,
            providerName = name,
        )
    }

    private class ToolCall.Builder {
        var id: String = ""
        val functionName = StringBuilder()
        val arguments = StringBuilder()

        fun build(): ToolCall = ToolCall(
            id = id,
            type = "function",
            function = ToolFunction(
                name = functionName.toString(),
                arguments = arguments.toString(),
            )
        )
    }
}
