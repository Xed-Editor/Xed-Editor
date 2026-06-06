package com.rk.ai.bridge.server

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.service.IdeNotificationSender
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SseManager(
    private val mcpDispatcher: McpDispatcher,
    private val ideContextJson: () -> String,
    private val onClientsChanged: (Int) -> Unit,
    private val scope: CoroutineScope,
) : IdeNotificationSender {

    private val sseClients = ConcurrentHashMap<String, PrintWriter>()
    private val sseLock = Any()
    val size: Int get() = synchronized(sseLock) { sseClients.size }
    private val keepaliveIntervalMs = 15_000L

    init {
        scope.launch {
            mcpDispatcher.streamingResponses.collect { (sessionId, payload) ->
                pushToSession(sessionId, mcpDispatcher.notificationJson("tool/stream", payload))
            }
        }
    }

    fun createSseStream(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val sessionId = UUID.randomUUID().toString()
        val (response, writer) = createStream(sessionId)
        val host = session.headers["host"] ?: "127.0.0.1"
        writer.print("event: endpoint\ndata: http://$host/messages?sessionId=$sessionId\n\n")
        writeInitialEvents(writer)
        startKeepalive(sessionId)
        return response
    }

    fun createMcpStream(requestedSessionId: String): NanoHTTPD.Response {
        val sessionId = requestedSessionId.takeIf { it.isNotBlank() } ?: "default"
        val (response, _) = createStream(sessionId)
        startKeepalive(sessionId)
        return response
    }

    private fun createStream(sessionId: String): Pair<NanoHTTPD.Response, PrintWriter> {
        val output = PipedOutputStream()
        val writer = PrintWriter(output, true)
        synchronized(sseLock) { sseClients[sessionId] = writer; onClientsChanged(sseClients.size) }
        val input = PipedInputStream(output)
        
        // Use a tracked input stream to detect closure
        val trackedInput = object : java.io.InputStream() {
            override fun read(): Int = input.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len)
            override fun available(): Int = input.available()
            override fun close() {
                input.close()
                output.close()
                synchronized(sseLock) {
                    if (sseClients.remove(sessionId) != null) {
                        onClientsChanged(sseClients.size)
                    }
                }
            }
        }

        val response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/event-stream", trackedInput, -1L).apply {
            addHeader("mcp-session-id", sessionId)
            addHeader("Cache-Control", "no-store")
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, MCP-Session-Id, mcp-session-id")
            addHeader("Access-Control-Expose-Headers", "MCP-Session-Id, mcp-session-id, MCP-Protocol-Version")
            addHeader("MCP-Protocol-Version", "2025-03-26")
            addHeader("Connection", "keep-alive")
        }
        return response to writer
    }

    private fun writeInitialEvents(writer: PrintWriter) {
        writer.print("event: message\ndata: ${mcpDispatcher.notificationJson("ide/contextUpdate", JsonParser.parseString(ideContextJson()).asJsonObject)}\n\n")
        writer.flush()
    }

    private fun startKeepalive(sessionId: String) {
        scope.launch {
            while (isActive) {
                delay(keepaliveIntervalMs)
                val writer = synchronized(sseLock) { sseClients[sessionId] } ?: return@launch
                val dead = runCatching {
                    synchronized(writer) {
                        writer.print(": keepalive\n\n")
                        writer.flush()
                        writer.checkError()
                    }
                }.getOrDefault(true)
                if (dead) {
                    synchronized(sseLock) {
                        if (sseClients.remove(sessionId) != null) {
                            onClientsChanged(sseClients.size)
                        }
                    }
                    return@launch
                }
            }
        }
    }


    fun closeSession(sessionId: String) {
        val writer = synchronized(sseLock) {
            sseClients.remove(sessionId).also { onClientsChanged(sseClients.size) }
        }
        runCatching { writer?.close() }
    }

    fun pushToSession(sessionId: String, responseBody: String): Boolean {
        val writer = synchronized(sseLock) { sseClients[sessionId] } ?: return false
        synchronized(writer) {
            writer.print("event: message\ndata: $responseBody\n\n")
            writer.flush()
        }
        return true
    }

    override fun sendNotification(method: String, params: JsonObject) {
        val notification = mcpDispatcher.notificationJson(method, params)
        val deadClients = mutableListOf<String>()
        synchronized(sseLock) {
            sseClients.forEach { (id, writer) ->
                runCatching {
                    synchronized(writer) {
                        writer.print("event: message\ndata: $notification\n\n")
                        writer.flush()
                    }
                    if (writer.checkError()) deadClients.add(id)
                }.onFailure { deadClients.add(id) }
            }
            deadClients.forEach { sseClients.remove(it) }
            onClientsChanged(sseClients.size)
        }
    }
}
