package com.rk.ai

import android.util.Log
import com.rk.ai.core.AiRequest
import com.rk.ai.core.RetryEngine
import com.rk.ai.core.RetryConfig
import com.rk.settings.Settings
import java.security.MessageDigest
import java.util.LinkedHashMap

object AiCompletionEngine {
    private const val TAG = "AiCompletion"
    private const val CACHE_MAX_SIZE = 32
    private const val CACHE_TTL_MS = 5000L
    private const val DEBOUNCE_MS = 300L

    private data class CacheEntry(val result: CompletionResult, val createdAtMs: Long)
    private val requestCache = LinkedHashMap<String, CacheEntry>(16, 0.75f, true)
    private val cacheLock = Any()
    private var lastRequestKey: String = ""
    private var lastRequestTime: Long = 0

    data class CompletionResult(
        val text: String,
        val line: Int,
        val column: Int,
    )

    private val retryConfig = RetryConfig(maxRetries = 2, baseDelayMs = 500L)

    suspend fun getInlineCompletion(
        filePath: String,
        content: String,
        cursorLine: Int,
        cursorColumn: Int,
        language: String,
    ): CompletionResult? {
        val normalized = content.replace("\r\n", "\n")
        val lines = normalized.split('\n')
        if (lines.isEmpty()) return null

        val cursorLineIndex = cursorLine.coerceIn(0, lines.lastIndex)
        val currentLine = lines[cursorLineIndex]
        val cursorColumnIndex = cursorColumn.coerceIn(0, currentLine.length)

        val beforeStart = maxOf(0, cursorLineIndex - 30)
        val afterEndExclusive = minOf(lines.size, cursorLineIndex + 4)
        val beforeLines = lines.subList(beforeStart, cursorLineIndex)
        val afterLines = lines.subList(cursorLineIndex + 1, afterEndExclusive)
        val linePrefix = currentLine.take(cursorColumnIndex)
        val lineSuffix = currentLine.drop(cursorColumnIndex)

        val contextBefore = (beforeLines + linePrefix).joinToString("\n").takeLast(4000)
        val contextAfter = (listOf(lineSuffix) + afterLines).joinToString("\n").take(2000)

        val cacheKey = fastHash(
            listOf(filePath, language, cursorLineIndex.toString(), cursorColumnIndex.toString(),
                contextBefore, contextAfter).joinToString("|")
        )
        val now = System.currentTimeMillis()

        synchronized(cacheLock) {
            if (cacheKey == lastRequestKey && now - lastRequestTime < DEBOUNCE_MS) return null
            lastRequestKey = cacheKey
            lastRequestTime = now
        }
        synchronized(cacheLock) {
            requestCache[cacheKey]?.let { entry ->
                if (now - entry.createdAtMs <= CACHE_TTL_MS) return entry.result
                requestCache.remove(cacheKey)
            }
        }

        val providerId = when {
            Settings.ai_agent == "gemini" -> "gemini"
            else -> "openai"
        }
        val model = Settings.ai_completion_model.ifBlank {
            com.rk.ai.core.ProviderManager.DEFAULT_MODELS[providerId] ?: "gpt-4o-mini"
        }
        val provider = com.rk.ai.core.AiCoreEngine.resolveProvider(providerId) ?: return null

        val systemPrompt = "You are a code completion engine. Given the code context before the cursor, output ONLY the most likely next code. No explanation, no markdown, no backticks. Complete the current line or add the next logical line. Match existing code style and indentation. Keep under 80 characters. If unsure, output nothing."
        val userPrompt = buildString {
            appendLine("Language: $language")
            appendLine("File: $filePath")
            appendLine("Cursor: ${cursorLineIndex + 1}:${cursorColumnIndex + 1}")
            appendLine()
            appendLine("Code before cursor:")
            appendLine(contextBefore)
            appendLine("CURSOR_HERE")
            if (contextAfter.isNotBlank()) {
                appendLine()
                appendLine("Code after cursor (for context):")
                appendLine(contextAfter)
            }
        }

        val request = AiRequest(
            prompt = userPrompt,
            systemPrompt = systemPrompt,
            model = model,
            temperature = 0.2f,
            maxTokens = 60,
            stream = false,
        )

        return try {
            val result = RetryEngine.retry(
                config = retryConfig,
                operation = "inline-completion",
                shouldRetry = { RetryEngine.isRetryable(it) },
            ) {
                provider.complete(request).getOrThrow()
            }
            val text = result.value.content.trim()
            if (text.isBlank()) return null

            val completionResult = CompletionResult(
                text = text,
                line = cursorLineIndex,
                column = cursorColumnIndex,
            )
            synchronized(cacheLock) {
                requestCache[cacheKey] = CacheEntry(result = completionResult, createdAtMs = now)
                if (requestCache.size > CACHE_MAX_SIZE) {
                    requestCache.keys.firstOrNull()?.let { requestCache.remove(it) }
                }
            }
            completionResult
        } catch (e: Exception) {
            if (com.rk.xededitor.BuildConfig.DEBUG) Log.e(TAG, "Completion failed", e)
            null
        }
    }

    private fun fastHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
