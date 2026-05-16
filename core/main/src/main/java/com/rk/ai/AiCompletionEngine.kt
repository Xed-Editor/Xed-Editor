package com.rk.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.session.AiSessionManager
import com.rk.settings.Settings
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object AiCompletionEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private const val TAG = "AiCompletion"
    private const val MAX_RETRIES = 2
    private val requestCache = LinkedHashMap<String, CompletionResult>(16, 0.75f, true)
    private const val CACHE_MAX_SIZE = 32
    private var lastRequestKey: String = ""
    private var lastRequestTime: Long = 0
    private const val DEBOUNCE_MS = 300L

    data class CompletionResult(
        val text: String,
        val line: Int,
        val column: Int,
    )

    private data class ApiConfig(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val provider: String = "openai",
    )

    private fun resolveConfig(): ApiConfig? {
        val agentName = AiSessionManager.currentAgent.name
        val customKey = Settings.ai_api_key.ifBlank { null }

        val provider = when (agentName) {
            "opencode" -> "openai"
            "gemini" -> "gemini"
            else -> "openai"
        }

        val model = when {
            Settings.ai_completion_model.isNotBlank() -> Settings.ai_completion_model
            provider == "gemini" -> "gemini-2.5-flash"
            provider == "opencode" -> "deepseek/deepseek-v4-flash"
            else -> "gpt-4o"
        }

        val key = when {
            customKey != null -> customKey
            provider == "gemini" -> System.getenv("GEMINI_API_KEY") ?: System.getenv("GOOGLE_API_KEY") ?: return null
            provider == "opencode" -> System.getenv("OPENCODE_API_KEY") ?: return null
            else -> return null
        }

        return ApiConfig(
            baseUrl = Settings.ai_completion_url.ifBlank {
                if (provider == "gemini") "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                else "https://api.openai.com/v1/chat/completions"
            },
            apiKey = key,
            model = model,
            provider = provider,
        )
    }

    suspend fun getInlineCompletion(
        filePath: String,
        content: String,
        cursorLine: Int,
        cursorColumn: Int,
        language: String,
    ): CompletionResult? = withContext(Dispatchers.IO) {
        val config = resolveConfig() ?: return@withContext null

        val cacheKey = "$filePath:$cursorLine:$cursorColumn:${content.length}"
        val now = System.currentTimeMillis()
        if (cacheKey == lastRequestKey && now - lastRequestTime < DEBOUNCE_MS) return@withContext null
        lastRequestKey = cacheKey
        lastRequestTime = now

        requestCache[cacheKey]?.let {
            if (now - lastRequestTime < 5000) return@withContext it
        }

        val lines = content.split("\n")
        val cursorIdx = cursorLine.coerceIn(1, lines.size)
        val windowStart = maxOf(0, cursorIdx - 1 - 30)
        val windowLines = lines.subList(windowStart, cursorIdx - 1)
        val afterLines = lines.subList(cursorIdx - 1, minOf(lines.size, cursorIdx + 2))
        val prefix = windowLines.joinToString("\n")
        val suffix = afterLines.joinToString("\n")

        val systemPrompt = "You are a code completion engine. Given the code context before the cursor, output ONLY the most likely next code. No explanation, no markdown, no backticks. Complete the current line or add the next logical line. Match existing code style and indentation. Keep under 80 characters. If unsure, output nothing."
        val userPrompt = buildString {
            appendLine("Language: $language")
            appendLine("File: $filePath")
            appendLine()
            appendLine("Code before cursor:")
            appendLine(prefix)
            appendLine("CURSOR_HERE")
            if (suffix.isNotBlank()) {
                appendLine()
                appendLine("Code after cursor (for context):")
                appendLine(suffix)
            }
        }

        val isGemini = config.provider == "gemini"

        val requestBody = if (isGemini) {
            JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", "$systemPrompt\n\n$userPrompt") })
                        })
                    })
                })
                add("generationConfig", JsonObject().apply {
                    addProperty("maxOutputTokens", 60)
                    addProperty("temperature", 0.2)
                    addProperty("topP", 0.9)
                    add("stopSequences", gson.toJsonTree(listOf("\n\n")))
                })
            }
        } else {
            JsonObject().apply {
                addProperty("model", config.model)
                add("messages", JsonArray().apply {
                    add(JsonObject().apply { addProperty("role", "system"); addProperty("content", systemPrompt) })
                    add(JsonObject().apply { addProperty("role", "user"); addProperty("content", userPrompt) })
                })
                addProperty("max_tokens", 60)
                addProperty("temperature", 0.2)
                addProperty("top_p", 0.9)
                add("stop", gson.toJsonTree(listOf("\n\n", "\n\r", "\r\n")))
            }
        }

        val requestUrl = if (isGemini) "$config.baseUrl?key=${config.apiKey}" else config.baseUrl

        for (attempt in 0..MAX_RETRIES) {
            try {
                val httpRequest = Request.Builder()
                    .url(requestUrl)
                    .addHeader("Content-Type", "application/json")
                    .apply {
                        if (!isGemini) addHeader("Authorization", "Bearer ${config.apiKey}")
                    }
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(httpRequest).execute()
                val body = response.body?.string() ?: continue

                if (!response.isSuccessful) {
                    if (attempt < MAX_RETRIES && response.code in 429..503) continue
                    if (BuildConfig.DEBUG) Log.w(TAG, "API error ${response.code}: $body")
                    return@withContext null
                }

                if (BuildConfig.DEBUG) Log.d(TAG, "API response: ${body.take(200)}")

                val text = if (isGemini) {
                    parseGeminiResponse(body)
                } else {
                    parseOpenAiResponse(body)
                } ?: continue

                if (text.isBlank()) return@withContext null

                val result = CompletionResult(text = text, line = cursorLine, column = cursorColumn)
                requestCache[cacheKey] = result
                if (requestCache.size > CACHE_MAX_SIZE) {
                    requestCache.keys.firstOrNull()?.let { requestCache.remove(it) }
                }
                return@withContext result
            } catch (e: Exception) {
                if (attempt < MAX_RETRIES) continue
                if (BuildConfig.DEBUG) Log.e(TAG, "Completion failed after $MAX_RETRIES retries", e)
            }
        }
        null
    }

    private fun parseGeminiResponse(body: String): String? {
        return try {
            val json = gson.fromJson(body, JsonObject::class.java)
            json.getAsJsonArray("candidates")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.get(0)?.asJsonObject
                ?.get("text")?.asString
                ?.trim()
        } catch (_: Exception) { null }
    }

    private fun parseOpenAiResponse(body: String): String? {
        return try {
            val json = gson.fromJson(body, JsonObject::class.java)
            json.getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
                ?.trim()
        } catch (_: Exception) { null }
    }
}
