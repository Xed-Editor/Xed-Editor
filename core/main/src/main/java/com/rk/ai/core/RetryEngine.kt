package com.rk.ai.core

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.random.Random

data class RetryConfig(
    val maxRetries: Int = 3,
    val baseDelayMs: Long = 1000L,
    val maxDelayMs: Long = 30_000L,
    val multiplier: Double = 2.0,
    val jitter: Double = 0.25,
)

data class RetryResult<T>(
    val value: T,
    val attempts: Int,
    val totalDurationMs: Long,
)

object RetryEngine {
    private const val TAG = "RetryEngine"

    suspend fun <T> retry(
        config: RetryConfig = RetryConfig(),
        timeoutMs: Long? = null,
        operation: String = "operation",
        shouldRetry: (Exception) -> Boolean = { true },
        block: suspend () -> T,
    ): RetryResult<T> {
        val startNanos = System.nanoTime()
        var lastException: Exception? = null

        for (attempt in 0..config.maxRetries) {
            val attemptStart = System.nanoTime()
            try {
                val result = if (timeoutMs != null) {
                    withTimeout(timeoutMs) { block() }
                } else {
                    block()
                }
                val durationMs = (System.nanoTime() - startNanos) / 1_000_000
                if (attempt > 0) {
                    Log.d(TAG, "$operation succeeded on attempt ${attempt + 1} (${durationMs}ms total)")
                }
                return RetryResult(result, attempt + 1, durationMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (!shouldRetry(e) || attempt >= config.maxRetries) {
                    Log.w(TAG, "$operation failed after ${attempt + 1} attempts: ${e.message}")
                    throw e
                }
                val delayMs = calculateDelay(attempt, config)
                Log.d(TAG, "$operation attempt ${attempt + 1} failed, retrying in ${delayMs}ms: ${e.message}")
                delay(delayMs)
            }
        }

        throw lastException ?: Exception("$operation failed after ${config.maxRetries + 1} attempts")
    }

    fun isRetryable(error: Throwable): Boolean = when {
        error is AiError.RateLimit -> true
        error is AiError.Network -> true
        error is AiError.Timeout -> true
        error is AiError.ProviderUnavailable -> true
        else -> false
    }

    fun extractRetryAfterMs(error: Throwable): Long = when (error) {
        is AiError.RateLimit -> error.retryAfterMs
        else -> 0L
    }

    private fun calculateDelay(attempt: Int, config: RetryConfig): Long {
        val exponential = (config.baseDelayMs * Math.pow(config.multiplier, attempt.toDouble())).toLong()
        val capped = exponential.coerceAtMost(config.maxDelayMs)
        val jitterAmount = (capped * config.jitter).toLong()
        val jittered = capped + Random.nextLong(-jitterAmount, jitterAmount)
        return jittered.coerceAtLeast(100L)
    }
}
