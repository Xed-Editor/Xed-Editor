package com.rk.ai.provider

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.pow

sealed class AiError(val retryable: Boolean, override val message: String) : Exception(message) {
    class RateLimited(msg: String) : AiError(true, "Rate limited: $msg")
    class Timeout(msg: String) : AiError(true, "Timeout: $msg")
    class AuthError(msg: String) : AiError(false, "Auth error: $msg")
    class ServerError(msg: String) : AiError(true, "Server error: $msg")
    class NetworkError(cause: Throwable) : AiError(true, "Network: ${cause.message}")
    class InvalidRequest(msg: String) : AiError(false, "Invalid request: $msg")
    class TokenLimit(msg: String) : AiError(false, "Token limit: $msg")
    class ProviderUnavailable(msg: String) : AiError(true, "Provider unavailable: $msg")
    class Unknown(cause: Throwable) : AiError(false, "${cause::class.java.simpleName}: ${cause.message ?: "unknown"}")
    class Cancelled : AiError(false, "Request cancelled")
}

object AiErrorClassifier {
    fun classify(exception: Throwable): AiError {
        val msg = exception.message?.lowercase() ?: ""
        val cls = exception::class.java.simpleName

        return when {
            exception is AiError -> exception
            exception is kotlinx.coroutines.TimeoutCancellationException -> AiError.Timeout(msg)
            exception is java.net.SocketTimeoutException -> AiError.Timeout(msg)
            exception is java.net.ConnectException -> AiError.NetworkError(exception)
            exception is java.net.UnknownHostException -> AiError.NetworkError(exception)
            exception is javax.net.ssl.SSLException -> AiError.NetworkError(exception)
            exception is java.io.IOException -> AiError.NetworkError(exception)
            exception is kotlinx.coroutines.CancellationException -> AiError.Cancelled()
            msg.contains("rate limit") || msg.contains("429") || msg.contains("too many requests") -> AiError.RateLimited(msg)
            msg.contains("401") || msg.contains("unauthorized") || msg.contains("api key") -> AiError.AuthError(msg)
            msg.contains("402") || msg.contains("insufficient") || msg.contains("quota") -> AiError.RateLimited(msg)
            msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("service unavailable") -> AiError.ServerError(msg)
            msg.contains("token limit") || msg.contains("max tokens") || msg.contains("context length") -> AiError.TokenLimit(msg)
            msg.contains("invalid") || msg.contains("bad request") || msg.contains("400") -> AiError.InvalidRequest(msg)
            else -> AiError.Unknown(exception)
        }
    }
}

class AiRetryPolicy(
    val maxRetries: Int = 3,
    private val baseDelayMs: Long = 2000L,
    private val maxDelayMs: Long = 30000L,
) {
    fun shouldRetry(attempt: Int, error: AiError): Boolean {
        if (!error.retryable) return false
        if (attempt >= maxRetries) return false
        return true
    }

    suspend fun delay(attempt: Int) {
        val exponential = baseDelayMs * 2.0.pow(attempt.toDouble())
        val jitter = (Math.random() * 1000).toLong()
        val delayMs = exponential.toLong().coerceAtMost(maxDelayMs) + jitter
        Log.d("AiRetry", "Retry attempt ${attempt + 1}/$maxRetries after ${delayMs}ms")
        delay(delayMs)
    }
}

object AiRequestTracker {
    private val activeRequests = ConcurrentHashMap<String, Long>()

    @Volatile var totalRequests = 0L
    @Volatile var totalTokens = 0L
    @Volatile var failedRequests = 0L
    @Volatile var successfulRequests = 0L

    fun track(requestId: String) {
        activeRequests[requestId] = System.currentTimeMillis()
        totalRequests++
    }

    fun complete(requestId: String) {
        activeRequests.remove(requestId)
        successfulRequests++
    }

    fun fail(requestId: String) {
        activeRequests.remove(requestId)
        failedRequests++
    }

    fun addTokens(count: Int) { totalTokens += count }

    fun activeCount(): Int = activeRequests.size

    fun snapshot(): String = buildString {
        appendLine("AI Requests:")
        appendLine("  Total: $totalRequests")
        appendLine("  Successful: $successfulRequests")
        appendLine("  Failed: $failedRequests")
        appendLine("  Active: ${activeRequests.size}")
        appendLine("  Total Tokens: $totalTokens")
    }

    fun reset() {
        activeRequests.clear()
        totalRequests = 0
        totalTokens = 0
        failedRequests = 0
        successfulRequests = 0
    }
}
