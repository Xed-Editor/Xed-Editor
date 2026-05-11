package com.rk.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private const val TAG = "AiCompletion"

    data class CompletionResult(
        val text: String,
        val line: Int,
        val column: Int,
    )

    suspend fun getInlineCompletion(
        filePath: String,
        content: String,
        cursorLine: Int,
        cursorColumn: Int,
        language: String,
    ): CompletionResult? = withContext(Dispatchers.IO) {
        val apiKey = Settings.ai_api_key.ifBlank {
            System.getenv("GEMINI_API_KEY") ?: System.getenv("GOOGLE_API_KEY") ?: return@withContext null
        }
        if (apiKey.isBlank()) return@withContext null

        val lines = content.split("\n")
        val prefixLines = lines.takeLast(50).dropLast(maxOf(0, lines.size - cursorLine - 1))
        val prefix = prefixLines.joinToString("\n")
        val beforeCursor = if (prefixLines.isNotEmpty()) {
            val lastLine = prefixLines.last()
            lastLine.take(cursorColumn)
        } else ""

        val prompt = buildString {
            appendLine("You are a code completion engine. Given the code context before the cursor, provide ONLY the most likely next code.")
            appendLine("Rules:")
            appendLine("- Output ONLY the completion text, no explanation, no markdown, no backticks")
            appendLine("- Complete the current line or add the next logical line")
            appendLine("- Match the existing code style and indentation")
            appendLine("- Keep completion under 80 characters")
            appendLine("- If unsure, output nothing")
            appendLine()
            appendLine("Language: $language")
            appendLine("File: $filePath")
            appendLine()
            appendLine("Code before cursor:")
            appendLine(prefix)
            appendLine("CURSOR_HERE")
        }

        try {
            val requestBody = JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("text", prompt)
                            })
                        })
                    })
                })
                add("generationConfig", JsonObject().apply {
                    addProperty("maxOutputTokens", 60)
                    addProperty("temperature", 0.2)
                    addProperty("topP", 0.9)
                    addProperty("stopSequences", gson.toJsonTree(listOf("\n\n", "\n\r", "\r\n")))
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            if (BuildConfig.DEBUG) Log.d(TAG, "API response: $body")

            val json = gson.fromJson(body, JsonObject::class.java)
            val candidates = json.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) return@withContext null

            val text = candidates[0].asJsonObject
                .getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.get(0)?.asJsonObject
                ?.get("text")?.asString
                ?.trim() ?: return@withContext null

            if (text.isBlank()) return@withContext null

            CompletionResult(
                text = text,
                line = cursorLine,
                column = cursorColumn,
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Completion failed", e)
            null
        }
    }
}
