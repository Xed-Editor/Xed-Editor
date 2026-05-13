package com.rk.ai.bridge.server

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.bridge.IdeNotificationSender
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
) : IdeNotificationSender {

    private val sseClients = ConcurrentHashMap<String, PrintWriter>()
    val size: Int get() = sseClients.size
    private val keepaliveIntervalMs = 15_000L

    fun createSseStream(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val sessionId = UUID.randomUUID().toString()
        val host = session.headers["host"]?.substringBefore(":") ?: "127.0.0.1"
        val port = session.headers["host"]?.substringAfter(":", "")?.takeIf { it.isNotBlank() } ?: "36765"
        val (response, writer) = createStream(sessionId)
        writer.print("event: endpoint\ndata: http://$host:$port/messages?sessionId=$sessionId\n\n")
        writeInitialEvents(writer)
        startKeepalive(sessionId)
        return response
    }

    fun createMcpStream(requestedSessionId: String): NanoHTTPD.Response {
        val sessionId = requestedSessionId.takeIf { it.isNotBlank() } ?: "default"
        val (response, writer) = createStream(sessionId)
        writeInitialEvents(writer)
        startKeepalive(sessionId)
        return response
    }

    private fun createStream(sessionId: String): Pair<NanoHTTPD.Response, PrintWriter> {
        val output = PipedOutputStream()
        val writer = PrintWriter(output, true)
        sseClients[sessionId] = writer
        onClientsChanged(sseClients.size)
        val input = PipedInputStream(output)
        val response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/event-stream", input, -1L).apply {
            addHeader("mcp-session-id", sessionId)
            addHeader("Cache-Control", "no-store")
            addHeader("Access-Control-Allow-Origin", "*")
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
        val deadClients = mutableListOf<String>()
        sseClients.forEach { (id, writer) ->
            runCatching {
                synchronized(writer) {
                    writer.print("event: message\n"); writer.print("data: $notification\n\n"); writer.flush()
                }
                if (writer.checkError()) deadClients.add(id)
            }.onFailure { deadClients.add(id) }
        }
        deadClients.forEach { sseClients.remove(it) }
        if (deadClients.isNotEmpty()) onClientsChanged(sseClients.size)
    }
}