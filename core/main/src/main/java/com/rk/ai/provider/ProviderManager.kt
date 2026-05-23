package com.rk.ai.provider

import android.util.Log
import com.rk.settings.Settings
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object ProviderManager {
    private val providers = ConcurrentHashMap<String, AiProvider>()
    private val TAG = "ProviderManager"

    init {
        register(OpenAiProvider())
        register(GeminiProvider())
    }

    fun register(provider: AiProvider) {
        providers[provider.name] = provider
    }

    fun getProvider(name: String): AiProvider? = providers[name]

    fun getProviderForAgent(agentName: String): AiProvider = when (agentName) {
        "gemini" -> providers["gemini"] ?: GeminiProvider().also { register(it) }
        else -> providers["openai"] ?: OpenAiProvider().also { register(it) }
    }

    fun allProviders(): List<AiProvider> = providers.values.toList()

    fun resolveConfig(agentName: String): ProviderConfig? {
        val customKey = com.rk.settings.SecureSettingsStore.get("ai_api_key").ifBlank { null }
        val providerType = when (agentName) {
            "gemini" -> ProviderType.GEMINI
            else -> ProviderType.OPENAI
        }
        val model = when {
            Settings.ai_completion_model.isNotBlank() -> Settings.ai_completion_model.trim()
            providerType == ProviderType.GEMINI -> "gemini-2.5-flash"
            else -> "gpt-4o-mini"
        }
        val key = customKey ?: when (providerType) {
            ProviderType.GEMINI -> System.getenv("GEMINI_API_KEY") ?: System.getenv("GOOGLE_API_KEY")
            else -> System.getenv("OPENAI_API_KEY") ?: System.getenv("OPENCODE_API_KEY") ?: System.getenv("OPENROUTER_API_KEY")
        } ?: return null

        val baseUrl = Settings.ai_completion_url.trim().ifBlank {
            when (providerType) {
                ProviderType.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/$model"
                ProviderType.OPENAI -> "https://api.openai.com/v1"
                else -> "https://api.openai.com/v1"
            }
        }

        return ProviderConfig(
            baseUrl = baseUrl,
            apiKey = key,
            model = model,
            providerType = providerType,
        )
    }
}

data class CompletionCacheEntry(
    val result: String,
    val createdAtMs: Long = System.currentTimeMillis(),
)

object CompletionCache {
    private val cache = LinkedHashMap<String, CompletionCacheEntry>(64, 0.75f, true)
    private const val MAX_SIZE = 64
    private const val TTL_MS = 10000L

    fun get(key: String): String? = synchronized(cache) {
        cache[key]?.let { entry ->
            if (System.currentTimeMillis() - entry.createdAtMs <= TTL_MS) {
                entry.result
            } else {
                cache.remove(key)
                null
            }
        }
    }

    fun put(key: String, result: String) = synchronized(cache) {
        cache[key] = CompletionCacheEntry(result)
        if (cache.size > MAX_SIZE) {
            cache.keys.firstOrNull()?.let { cache.remove(it) }
        }
    }

    fun clear() = synchronized(cache) { cache.clear() }

    fun fastHash(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(parts.joinToString("|").toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

class NewCompletionEngine {
    private val TAG = "NewCompletionEngine"
    private val retryPolicy = AiRetryPolicy()
    private var lastRequestKey: String = ""
    private var lastRequestTime: Long = 0
    private val debounceMs = 300L

    suspend fun getInlineCompletion(
        filePath: String,
        content: String,
        cursorLine: Int,
        cursorColumn: Int,
        language: String,
        agentName: String = "opencode",
    ): CompletionResponse? {
        val config = ProviderManager.resolveConfig(agentName) ?: return null
        val provider = ProviderManager.getProviderForAgent(agentName)

        val normalized = content.replace("\r\n", "\n")
        val lines = normalized.split('\n')
        if (lines.isEmpty()) return null

        val cursorLineIndex = cursorLine.coerceIn(0, lines.lastIndex)
        val currentLine = lines[cursorLineIndex]
        val cursorColumnIndex = cursorColumn.coerceIn(0, currentLine.length)

        val beforeStart = maxOf(0, cursorLineIndex - 30)
        val afterEnd = minOf(lines.size, cursorLineIndex + 4)
        val beforeLines = lines.subList(beforeStart, cursorLineIndex)
        val afterLines = lines.subList(cursorLineIndex + 1, afterEnd)
        val linePrefix = currentLine.take(cursorColumnIndex)
        val lineSuffix = currentLine.drop(cursorColumnIndex)

        val contextBefore = (beforeLines + linePrefix).joinToString("\n").takeLast(4000)
        val contextAfter = (listOf(lineSuffix) + afterLines).joinToString("\n").take(2000)

        val cacheKey = CompletionCache.fastHash(
            filePath, language, cursorLineIndex.toString(), cursorColumnIndex.toString(),
            agentName, config.model, contextBefore, contextAfter,
        )

        val now = System.currentTimeMillis()
        if (cacheKey == lastRequestKey && now - lastRequestTime < debounceMs) return null
        lastRequestKey = cacheKey
        lastRequestTime = now

        CompletionCache.get(cacheKey)?.let { cached ->
            return CompletionResponse(
                id = "cached",
                content = cached,
                finishReason = FinishReason.STOP,
                providerName = "cache",
            )
        }

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

        val request = CompletionRequest(
            messages = listOf(
                Message(MessageRole.USER, userPrompt),
            ),
            config = config.copy(maxTokens = 60, temperature = 0.2f, topP = 0.9f),
            systemPrompt = systemPrompt,
            stopSequences = listOf("\n\n", "\n\r", "\r\n"),
            cacheKey = cacheKey,
        )

        var lastError: Throwable? = null
        for (attempt in 0..retryPolicy.maxRetries) {
            try {
                val response = provider.complete(request)
                if (response.content.isNotBlank()) {
                    CompletionCache.put(cacheKey, response.content)
                }
                AiRequestTracker.addTokens(response.usage?.totalTokens ?: 0)
                return response
            } catch (e: Exception) {
                lastError = e
                val classified = AiErrorClassifier.classify(e)
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${classified.message}")
                if (retryPolicy.shouldRetry(attempt, classified)) {
                    retryPolicy.delay(attempt)
                } else {
                    break
                }
            }
        }
        lastError?.let { Log.e(TAG, "All attempts failed", it) }
        return null
    }
}
