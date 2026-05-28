package com.rk.ai.bridge.server

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HttpSessionTracker(private val onClientsChanged: (Int) -> Unit) {

    private val httpClientSessions = ConcurrentHashMap<String, Long>()
    private var sseCount: Int = 0

    val totalConnectedClients: Int get() = sseCount + httpClientSessions.size
    val httpSessions: Map<String, Long> get() = httpClientSessions
    val httpSessionCount: Int get() = httpClientSessions.size
    val sseSessionCount: Int get() = sseCount
    val sseSessionIds: Set<String> get() = emptySet()

    fun updateSseCount(count: Int) { sseCount = count; onClientsChanged(totalConnectedClients) }

    fun createSession(): String {
        val sessionId = UUID.randomUUID().toString()
        httpClientSessions[sessionId] = System.currentTimeMillis()
        onClientsChanged(totalConnectedClients)
        return sessionId
    }

    fun touchSession(sessionId: String) {
        httpClientSessions[sessionId] = System.currentTimeMillis()
    }

    fun removeSession(sessionId: String) {
        httpClientSessions.remove(sessionId)
        onClientsChanged(totalConnectedClients)
    }

    fun cleanupStale() {
        val deadline = System.currentTimeMillis() - 300_000
        httpClientSessions.entries.removeIf { it.value < deadline }
        onClientsChanged(totalConnectedClients)
    }

    fun startBackgroundCleanup(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                delay(60_000)
                cleanupStale()
            }
        }
    }
}
