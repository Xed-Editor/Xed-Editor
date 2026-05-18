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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SseManager(
    private val mcpDispatcher: McpDispatcher,
    private val ideContextJson: () -> String,
    private val onClientsChanged: (Int) -> Unit,
    private val scope: CoroutineScope,
    private val token: String,
    private val portProvider: () -> Int = { 0 },
) : IdeNotificationSender {

    private val sseClients = ConcurrentHashMap<String, SendChannel<String>>()
    val size: Int get() = sseClients.size
    private val keepaliveIntervalMs = 15_000L
    private val notificationQueue = LinkedBlockingQueue<Pair<String, String>>(1000)
    private val batchIntervalMs = 50L
    private var lastDeadClientCheck = 0L

    private companion object {
        private const val PIPE_BUFFER_SIZE = 65536
    }

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
                    sseClients.entries.toList().forEach { (id, channel) ->
                        var failed = false
                        batch.forEach { (eventType, data) ->
                            if (!channel.trySend("event: $eventType\ndata: $data\n\n").isSuccess) failed = true
                        }
                        if (failed) deadClients.add(id)
                    }
                }

                val now = System.currentTimeMillis()
                if (deadClients.isEmpty() && batch.isNotEmpty() && now - lastDeadClientCheck < 5000) {
                    delay(batchIntervalMs)
                    continue
                }
                if (deadClients.isNotEmpty() || now - lastDeadClientCheck >= 5000) {
                    lastDeadClientCheck = now
                    sseClients.entries.toList().forEach { (id, channel) ->
                        if (!channel.trySend(": keepalive\n\n").isSuccess) deadClients.add(id)
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
        val (response, channel) = createStream(sessionId)
        sendEndpointEvent(session, sessionId, channel)
        writeInitialEvents(channel)
        startKeepalive(sessionId)
        return response
    }

    fun createMcpStream(session: NanoHTTPD.IHTTPSession, requestedSessionId: String): NanoHTTPD.Response {
        val sessionId = requestedSessionId.takeIf { it.isNotBlank() } ?: "default"
        val (response, channel) = createStream(sessionId)
        sendEndpointEvent(session, sessionId, channel)
        writeInitialEvents(channel)
        startKeepalive(sessionId)
        return response
    }

    private fun sendEndpointEvent(session: NanoHTTPD.IHTTPSession, sessionId: String, channel: SendChannel<String>) {
        val hostHeader = session.headers["host"] ?: "127.0.0.1"
        val host = hostHeader.substringBefore(":").ifEmpty { "127.0.0.1" }
        val port = hostHeader.substringAfter(":", "").ifEmpty {
            portProvider().takeIf { it > 0 }?.toString() ?: "36765"
        }
        val url = if (port.isBlank()) "http://$host/messages?sessionId=$sessionId&token=$token"
                  else "http://$host:$port/messages?sessionId=$sessionId&token=$token"
        channel.trySend("event: endpoint\ndata: $url\n\n")
    }

    private fun writeInitialEvents(channel: SendChannel<String>) {
        channel.trySend("event: message\ndata: ${mcpDispatcher.notificationJson("ide/contextUpdate", JsonParser.parseString(ideContextJson()).asJsonObject)}\n\n")
    }

    private fun createStream(sessionId: String): Pair<NanoHTTPD.Response, SendChannel<String>> {
        val output = PipedOutputStream()
        val input = PipedInputStream(output, PIPE_BUFFER_SIZE)
        val channel = Channel<String>(Channel.BUFFERED)

        sseClients[sessionId] = channel
        onClientsChanged(sseClients.size)

        scope.launch(Dispatchers.IO) {
            val writer = PrintWriter(output, true)
            try {
                for (event in channel) {
                    writer.print(event)
                    writer.flush()
                    if (writer.checkError()) break
                }
            } catch (_: Exception) {
            } finally {
                runCatching { output.close() }
                runCatching { input.close() }
                sseClients.remove(sessionId)
                onClientsChanged(sseClients.size)
            }
        }

        val response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/event-stream", input, -1L).apply {
            addHeader("mcp-session-id", sessionId)
            addHeader("Cache-Control", "no-store")
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Connection", "keep-alive")
        }
        return response to channel
    }

    private fun startKeepalive(sessionId: String) {
        scope.launch {
            var consecutiveErrors = 0
            while (isActive) {
                delay(keepaliveIntervalMs)
                val channel = sseClients[sessionId] ?: break
                val result = channel.trySend(": keepalive\n\n")
                if (!result.isSuccess) {
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
        val channel = sseClients[sessionId] ?: return false
        val result = channel.trySend("event: message\ndata: $responseBody\n\n")
        return result.isSuccess
    }

    override fun sendNotification(method: String, params: JsonObject) {
        val notification = mcpDispatcher.notificationJson(method, params)
        if (sseClients.isEmpty()) return
        notificationQueue.offer("message" to notification)
    }
}
