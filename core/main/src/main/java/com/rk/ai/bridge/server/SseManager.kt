package com.rk.ai.bridge.server

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.bridge.IdeNotificationSender
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SseManager(
    private val mcpDispatcher: McpDispatcher,
    private val ideContextJson: () -> String,
    private val onClientsChanged: (Int) -> Unit,
    private val scope: CoroutineScope,
    private val portProvider: () -> Int = { 0 },
) : IdeNotificationSender {

    private val sseClients = ConcurrentHashMap<String, PrintWriter>()
    val size: Int get() = sseClients.size
    private val keepaliveIntervalMs = 15_000L
    private val notificationQueue = LinkedBlockingQueue<Pair<String, String>>(1000)
    private val batchIntervalMs = 50L
    private var lastDeadClientCheck = 0L

    init {
        startNotificationBatcher()
    }

    private fun startNotificationBatcher() {
        scope.launch {
            while (isActive) {
                val deadClients = mutableListOf<String>()
                val batch = mutableListOf<Pair<String, String>>()
                notificationQueue.drainTo(batch)

                if (batch.isNotEmpty()) {
                    sseClients.entries.toList().forEach { entry ->
                        val id = entry.key
                        val writer = entry.value
                        runCatching {
                            synchronized(writer) {
                                batch.forEach { (eventType, data) ->
                                    writer.print("event: $eventType\n")
                                    writer.print("data: $data\n\n")
                                }
                                writer.flush()
                            }
                            if (writer.checkError()) deadClients.add(id)
                        }.onFailure { deadClients.add(id) }
                    }
                }

                val now = System.currentTimeMillis()
                if (deadClients.isEmpty() && batch.isNotEmpty() && now - lastDeadClientCheck < 5000) {
                    delay(batchIntervalMs)
                    continue
                }
                if (deadClients.isNotEmpty() || now - lastDeadClientCheck >= 5000) {
                    lastDeadClientCheck = now
                    sseClients.entries.toList().forEach { entry ->
                        val id = entry.key
                        val writer = entry.value
                        if (writer.checkError()) deadClients.add(id)
                    }
                    if (deadClients.isNotEmpty()) {
                        deadClients.toSet().forEach { sseClients.remove(it) }
                        onClientsChanged(sseClients.size)
                    }
                }
                if (batch.isEmpty()) delay(batchIntervalMs)
            }
        }
    }

    fun createSseStream(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val sessionId = UUID.randomUUID().toString()
        val (response, writer) = createStream(sessionId)
        sendEndpointEvent(session, sessionId, writer)
        writeInitialEvents(writer)
        startKeepalive(sessionId)
        return response
    }

    fun createMcpStream(session: NanoHTTPD.IHTTPSession, requestedSessionId: String): NanoHTTPD.Response {
        val sessionId = requestedSessionId.takeIf { it.isNotBlank() } ?: "default"
        val (response, writer) = createStream(sessionId)
        sendEndpointEvent(session, sessionId, writer)
        writeInitialEvents(writer)
        startKeepalive(sessionId)
        return response
    }

    private fun sendEndpointEvent(session: NanoHTTPD.IHTTPSession, sessionId: String, writer: PrintWriter) {
        val hostHeader = session.headers["host"] ?: "127.0.0.1"
        val host = hostHeader.substringBefore(":").ifEmpty { "127.0.0.1" }
        val port = hostHeader.substringAfter(":", "").ifEmpty {
            portProvider().takeIf { it > 0 }?.toString() ?: "36765"
        }
        val url = if (port.isBlank()) "http://$host/messages?sessionId=$sessionId"
                  else "http://$host:$port/messages?sessionId=$sessionId"
        writer.print("event: endpoint\ndata: $url\n\n")
        writer.flush()
    }

    private companion object {
        private const val PIPE_BUFFER_SIZE = 65536
    }

    private fun createStream(sessionId: String): Pair<NanoHTTPD.Response, PrintWriter> {
        val output = PipedOutputStream()
        val writer = PrintWriter(output, true)
        sseClients[sessionId] = writer
        onClientsChanged(sseClients.size)
        val input = PipedInputStream(output, PIPE_BUFFER_SIZE)
        val response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/event-stream", input, -1L).apply {
            addHeader("mcp-session-id", sessionId)
            addHeader("Cache-Control", "no-store")
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Connection", "keep-alive")
        }
        return response to writer
    }

    private fun writeInitialEvents(writer: PrintWriter) {
        writer.print("event: message\ndata: ${mcpDispatcher.notificationJson("ide/contextUpdate", JsonParser.parseString(ideContextJson()).asJsonObject)}\n\n")
        writer.print("event: message\ndata: ${mcpDispatcher.notificationJson("initialized", JsonObject())}\n\n")
        writer.flush()
    }

    private fun startKeepalive(sessionId: String) {
        scope.launch {
            var consecutiveErrors = 0
            while (isActive) {
                delay(keepaliveIntervalMs)
                val w = sseClients[sessionId] ?: break
                w.print(": keepalive\n\n"); w.flush()
                if (w.checkError()) {
                    consecutiveErrors++
                    if (consecutiveErrors >= 3) {
                        sseClients.remove(sessionId)
                        onClientsChanged(sseClients.size)
                        break
                    }
                } else {
                    consecutiveErrors = 0
                }
            }
        }
    }

    fun pushToSession(sessionId: String, responseBody: String): Boolean {
        val writer = sseClients[sessionId] ?: return false
        synchronized(writer) {
            writer.print("event: message\n"); writer.print("data: $responseBody\n\n"); writer.flush()
        }
        return true
    }

    override fun sendNotification(method: String, params: JsonObject) {
        val notification = mcpDispatcher.notificationJson(method, params)
        if (sseClients.isEmpty()) return
        notificationQueue.offer("message" to notification)
    }
}
