package com.rk.ai.bridge.server

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HttpSessionTracker(private val onClientsChanged: (Int) -> Unit) {

    private val httpClientSessions = ConcurrentHashMap<String, SessionEntry>()
    private var sseCount: Int = 0

    data class SessionEntry(val createdAt: Long, var lastTouched: Long)

    val totalConnectedClients: Int get() = sseCount + httpClientSessions.size
    val httpSessions: Map<String, Long> get() = httpClientSessions.mapValues { it.value.lastTouched }
    val httpSessionCount: Int get() = httpClientSessions.size
    val sseSessionCount: Int get() = sseCount

    fun updateSseCount(count: Int) {
        sseCount = count
        onClientsChanged(totalConnectedClients)
    }

    fun createSession(): String {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        httpClientSessions[sessionId] = SessionEntry(now, now)
        onClientsChanged(totalConnectedClients)
        return sessionId
    }

    fun touchSession(sessionId: String) {
        httpClientSessions[sessionId]?.lastTouched = System.currentTimeMillis()
    }

    fun removeSession(sessionId: String) {
        httpClientSessions.remove(sessionId)
        onClientsChanged(totalConnectedClients)
    }

    fun cleanupStale(maxAgeMs: Long = 300_000) {
        val deadline = System.currentTimeMillis() - maxAgeMs
        httpClientSessions.entries.removeIf { it.value.lastTouched < deadline }
        onClientsChanged(totalConnectedClients)
    }

    fun startBackgroundCleanup(scope: CoroutineScope, intervalMs: Long = 60_000) {
        scope.launch {
            while (isActive) {
                delay(intervalMs)
                cleanupStale()
            }
        }
    }
}
