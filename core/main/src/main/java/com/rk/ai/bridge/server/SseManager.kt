package com.rk.ai.bridge.server

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.bridge.IdeNotificationSender
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "SseManager"

class SseManager(
    private val mcpDispatcher: McpDispatcher,
    private val ideContextJson: () -> String,
    private val onClientsChanged: (Int) -> Unit,
    private val scope: CoroutineScope,
    private val token: String,
    private val portProvider: () -> Int = { 0 },
) : IdeNotificationSender {

    private val sseClients = ConcurrentHashMap<String, SseClient>()
    val size: Int get() = sseClients.size

    private val keepaliveIntervalMs = 15_000L
    private val notificationQueue = LinkedBlockingQueue<Pair<String, String>>(1000)
    private val batchIntervalMs = 50L
    private val clientTimeoutMs = 60_000L

    init {
        startNotificationBatcher()
        startDeadClientCollector()
    }

    private fun startNotificationBatcher() {
        scope.launch {
            while (isActive) {
                val batch = mutableListOf<Pair<String, String>>()
                notificationQueue.drainTo(batch)
                if (batch.isNotEmpty()) {
                    sseClients.entries.toList().forEach { (id, client) ->
                        batch.forEach { (eventType, data) ->
                            client.enqueue("event: $eventType\ndata: $data\n\n")
                        }
                    }
                }
                if (batch.isEmpty()) delay(batchIntervalMs)
            }
        }
    }

    private fun startDeadClientCollector() {
        scope.launch {
            while (isActive) {
                delay(15_000L)
                val now = System.currentTimeMillis()
                val dead = mutableListOf<String>()
                sseClients.entries.forEach { (id, client) ->
                    if (now - client.createdAt > clientTimeoutMs && !client.hasData()) {
                        if (!client.enqueue(": keepalive\n\n")) {
                            dead.add(id)
                        }
                    }
                    if (client.isClosed()) {
                        dead.add(id)
                    }
                }
                if (dead.isNotEmpty()) {
                    dead.toSet().forEach { id ->
                        sseClients.remove(id)?.close()
                    }
                    onClientsChanged(sseClients.size)
                }
                if (sseClients.isNotEmpty()) {
                    sseClients.entries.forEach { (_, client) ->
                        client.enqueue(": keepalive\n\n")
                    }
                }
            }
        }
    }

    fun createSseStream(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val sessionId = UUID.randomUUID().toString()
        val response = createStream(session, sessionId)
        return response
    }

    fun createMcpStream(session: NanoHTTPD.IHTTPSession, requestedSessionId: String): NanoHTTPD.Response {
        val sessionId = requestedSessionId.takeIf { it.isNotBlank() } ?: "default"
        return createStream(session, sessionId)
    }

    private fun sendEndpointEvent(session: NanoHTTPD.IHTTPSession, client: SseClient) {
        val hostHeader = session.headers["host"] ?: "127.0.0.1"
        val host = hostHeader.substringBefore(":").ifEmpty { "127.0.0.1" }
        val port = hostHeader.substringAfter(":", "").ifEmpty {
            portProvider().takeIf { it > 0 }?.toString() ?: "36765"
        }
        val url = if (port.isBlank()) "http://$host/messages?sessionId=${client.sessionId}&token=$token"
                  else "http://$host:$port/messages?sessionId=${client.sessionId}&token=$token"
        client.enqueue("event: endpoint\ndata: $url\n\n")
    }

    private fun writeInitialEvents(client: SseClient) {
        client.enqueue("event: message\ndata: ${mcpDispatcher.notificationJson("ide/contextUpdate", JsonParser.parseString(ideContextJson()).asJsonObject)}\n\n")
    }

    private fun createStream(session: NanoHTTPD.IHTTPSession, sessionId: String): NanoHTTPD.Response {
        val client = SseClient(sessionId, keepaliveIntervalMs)
        sseClients[sessionId] = client
        onClientsChanged(sseClients.size)

        sendEndpointEvent(session, client)
        writeInitialEvents(client)

        val stream = client.createInputStream()

        val response = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "text/event-stream",
            stream,
            -1L
        ).apply {
            addHeader("mcp-session-id", sessionId)
            addHeader("Cache-Control", "no-store")
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Connection", "keep-alive")
        }

        scope.launch(Dispatchers.IO) {
            while (isActive && !client.isClosed()) {
                delay(keepaliveIntervalMs)
                client.enqueue(": keepalive\n\n")
            }
        }

        return response
    }

    fun pushToSession(sessionId: String, responseBody: String): Boolean {
        val client = sseClients[sessionId] ?: return false
        return client.enqueue("event: message\ndata: $responseBody\n\n")
    }

    override fun sendNotification(method: String, params: JsonObject) {
        val notification = mcpDispatcher.notificationJson(method, params)
        if (sseClients.isEmpty()) return
        notificationQueue.offer("message" to notification)
    }

    private class SseClient(
        val sessionId: String,
        private val keepaliveIntervalMs: Long,
    ) {
        val createdAt: Long = System.currentTimeMillis()
        private val queue: BlockingQueue<ByteArray?> = LinkedBlockingQueue(128)
        @Volatile private var closed = false

        fun enqueue(message: String): Boolean {
            if (closed) return false
            val data = message.toByteArray(Charsets.UTF_8)
            return queue.offer(data, 1, TimeUnit.SECONDS)
        }

        fun hasData(): Boolean = queue.isNotEmpty()

        fun isClosed(): Boolean = closed

        fun close() {
            closed = true
            queue.offer(null)
        }

        fun createInputStream(): InputStream {
            return object : InputStream() {
                private var currentBuffer: ByteArray? = null
                private var currentOffset = 0

                override fun read(): Int {
                    val buffer = getCurrentBuffer() ?: return -1
                    return buffer[currentOffset++].toInt() and 0xFF
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val buffer = getCurrentBuffer() ?: return -1
                    val available = buffer.size - currentOffset
                    val toRead = minOf(available, len)
                    System.arraycopy(buffer, currentOffset, b, off, toRead)
                    currentOffset += toRead
                    return toRead
                }

                override fun available(): Int {
                    val buf = currentBuffer
                    return if (buf != null) buf.size - currentOffset else if (queue.isNotEmpty()) 1 else 0
                }

                private fun getCurrentBuffer(): ByteArray? {
                    if (currentBuffer != null && currentOffset < currentBuffer!!.size) {
                        return currentBuffer
                    }
                    currentBuffer = null
                    currentOffset = 0
                    try {
                        currentBuffer = queue.poll(30, TimeUnit.SECONDS)
                    } catch (_: InterruptedException) {
                        return null
                    }
                    if (currentBuffer == null) {
                        closed = true
                        return null
                    }
                    return currentBuffer
                }

                override fun close() {
                    this@SseClient.close()
                }
            }
        }
    }
}
