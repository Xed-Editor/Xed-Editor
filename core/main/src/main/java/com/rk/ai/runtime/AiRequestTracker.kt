package com.rk.ai.runtime

import java.util.concurrent.ConcurrentHashMap

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
