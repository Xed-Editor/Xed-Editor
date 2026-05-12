package com.rk.ai.bridge.server

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.service.IdeNotificationSender
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import fi.iki.elonen.NanoHTTPD

class SseManager(
    private val mcpDispatcher: McpDispatcher,
    private val ideContextJson: () -> String,
    private val onClientsChanged: (Int) -> Unit
) : IdeNotificationSender {

    private val sseClients = ConcurrentHashMap<String, PrintWriter>()
    private val sseLock = Any()
    val size: Int get() = synchronized(sseLock) { sseClients.size }

    fun createSseStream(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val sessionId = UUID.randomUUID().toString()
        val output = PipedOutputStream()
        val writer = PrintWriter(output, true)
        synchronized(sseLock) { sseClients[sessionId] = writer; onClientsChanged(sseClients.size) }
        val host = session.headers["host"] ?: "127.0.0.1"
        writer.print("event: endpoint\ndata: http://$host/messages?sessionId=$sessionId\n\n")
        writer.print("event: message\ndata: ${mcpDispatcher.notificationJson("ide/contextUpdate", JsonParser.parseString(ideContextJson()).asJsonObject)}\n\n")
        writer.print("event: message\ndata: ${mcpDispatcher.notificationJson("initialized", JsonObject())}\n\n")
        writer.flush()
        Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(15000)
                    synchronized(sseLock) {
                        val w = sseClients[sessionId] ?: return@synchronized
                        w.print(": keepalive\n\n"); w.flush()
                        if (w.checkError()) {
                            sseClients.remove(sessionId); onClientsChanged(sseClients.size); Thread.currentThread().interrupt()
                        }
                    }
                } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }
        val input = PipedInputStream(output)
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/event-stream", input, -1L).apply {
            addHeader("mcp-session-id", sessionId)
            addHeader("Cache-Control", "no-store")
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    fun createMcpStream(requestedSessionId: String): NanoHTTPD.Response {
        val sessionId = requestedSessionId.takeIf { it.isNotBlank() } ?: "default"
        val output = PipedOutputStream()
        val writer = PrintWriter(output, true)
        synchronized(sseLock) { sseClients[sessionId] = writer; onClientsChanged(sseClients.size) }
        writer.print("event: message\ndata: ${mcpDispatcher.notificationJson("ide/contextUpdate", JsonParser.parseString(ideContextJson()).asJsonObject)}\n\n")
        writer.print("event: message\ndata: ${mcpDispatcher.notificationJson("initialized", JsonObject())}\n\n")
        writer.flush()
        Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(15000)
                    synchronized(sseLock) {
                        val w = sseClients[sessionId] ?: return@synchronized
                        w.print(": keepalive\n\n"); w.flush()
                        if (w.checkError()) {
                            sseClients.remove(sessionId); onClientsChanged(sseClients.size); Thread.currentThread().interrupt()
                        }
                    }
                } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }
        val input = PipedInputStream(output)
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/event-stream", input, -1L).apply {
            addHeader("mcp-session-id", sessionId)
            addHeader("Cache-Control", "no-store")
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    fun pushToSession(sessionId: String, responseBody: String): Boolean {
        val writer = synchronized(sseLock) { sseClients[sessionId] }
        if (writer != null) {
            synchronized(sseLock) {
                writer.print("event: message\n"); writer.print("data: $responseBody\n\n"); writer.flush()
            }
            return true
        }
        return false
    }

    override fun sendNotification(method: String, params: JsonObject) {
        val notification = mcpDispatcher.notificationJson(method, params)
        val deadClients = mutableListOf<String>()
        synchronized(sseLock) {
            sseClients.forEach { (id, writer) ->
                runCatching {
                    writer.print("event: message\n"); writer.print("data: $notification\n\n"); writer.flush()
                    if (writer.checkError()) deadClients.add(id)
                }.onFailure { deadClients.add(id) }
            }
            deadClients.forEach { sseClients.remove(it) }
            onClientsChanged(sseClients.size)
        }
    }
}
