package com.rk.ai.bridge.server

import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.GeminiBridge
import com.rk.ai.GeminiMcpTools
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.bridge.tools.*
import com.rk.ai.service.GeminiIdeService
import com.rk.ai.service.GeminiNotificationSender
import com.rk.xededitor.BuildConfig
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class GeminiBridgeServer(
    val port: Int,
    private val token: String,
    initialIdeService: GeminiIdeService
) : NanoHTTPD(port), GeminiNotificationSender {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var toolRegistry = McpToolRegistry(initialIdeService)
    
    var ideService: GeminiIdeService = initialIdeService
        set(value) {
            field = value
            toolRegistry = McpToolRegistry(value)
            registerTools()
        }

    init {
        registerTools()
    }

    private fun registerTools() {
        toolRegistry.apply {
            register(ReadFileTool())
            register(WriteFileTool())
            register(ListFilesTool())
            register(OpenFileTool())
            register(GetOpenFilesTool())
            register(GetActiveFileTool())
            register(GetSelectionTool())
            register(ReplaceSelectionTool())
            register(InsertAtCursorTool())
            register(SaveOpenFilesTool())
            register(RefreshOpenEditorsTool())
            register(RefreshFileTool())
            register(OpenDiffTool())
            register(CloseDiffTool())
            register(RunCommandTool())
            register(ShowMessageTool())
        }
    }

    private val sseClients = ConcurrentHashMap<String, PrintWriter>()

    private fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d("GeminiBridgeServer", msg)
    }

    override fun serve(session: IHTTPSession): Response {
        d("serve ${session.method} ${session.uri}")

        // parseBody must be called to access session.parameters for POST requests
        if (session.method == Method.POST) {
            runCatching { session.parseBody(mutableMapOf()) }
        }

        if (!hasValidHost(session)) {
            return json(Response.Status.FORBIDDEN, errorJson(null, -32003, "invalid host"))
        }
        if (!isAuthorized(session)) {
            return json(Response.Status.UNAUTHORIZED, errorJson(null, -32001, "unauthorized"))
        }

        return when (session.uri) {
            "/health" -> json(Response.Status.OK, "{\"ok\":true}")
            "/context" -> json(Response.Status.OK, ideContextJson())
            "/refresh" -> {
                ideService.refreshEditors()
                json(Response.Status.OK, "{\"ok\":true}")
            }
            "/mcp" -> serveMcp(session)
            else -> json(Response.Status.NOT_FOUND, errorJson(null, -32601, "not_found"))
        }
    }

    private fun serveMcp(session: IHTTPSession): Response {
        if (session.method == Method.GET) {
            return serveMcpStream(session)
        }

        val raw = readRequestBodyUtf8(session).getOrElse {
            return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, it.message ?: "invalid request"))
        }

        val request = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "parse error"))

        val id = request.get("id")?.asString ?: "null"
        val method = request.get("method")?.asString.orEmpty()

        return when (method) {
            "initialize" -> json(Response.Status.OK, initializeResult(id))
            "tools/list" -> json(Response.Status.OK, toolsListResult(id))
            "tools/call" -> json(Response.Status.OK, toolsCallResult(id, request))
            "notifications/initialized" -> json(Response.Status.OK, resultJson(id, JsonObject()))
            else -> json(Response.Status.OK, errorJson(id, -32601, "method not found: $method"))
        }
    }

    private fun serveMcpStream(session: IHTTPSession): Response {
        val requestedSessionId = session.parameters["sessionId"]?.firstOrNull() ?: "default"
        val output = PipedOutputStream()
        val input = PipedInputStream(output)
        val writer = PrintWriter(output)

        sseClients[requestedSessionId] = writer
        
        // Notify IDE context on connection
        runBlocking {
            writer.print("event: message\n")
            writer.print("data: ${notificationJson("ide/contextUpdate", JsonParser.parseString(ideContextJson()).asJsonObject)}\n\n")
            writer.flush()
        }

        return newChunkedResponse(Response.Status.OK, "text/event-stream", input).apply {
            addHeader("mcp-session-id", requestedSessionId)
            addHeader("Cache-Control", "no-store")
            addHeader("Connection", "keep-alive")
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    override fun sendNotification(method: String, params: JsonObject) {
        val notification = notificationJson(method, params)
        val deadClients = mutableListOf<String>()
        sseClients.forEach { (id, writer) ->
            runCatching {
                writer.print("event: message\n")
                writer.print("data: $notification\n\n")
                writer.flush()
                if (writer.checkError()) deadClients.add(id)
            }.onFailure { deadClients.add(id) }
        }
        deadClients.forEach { sseClients.remove(it) }
    }

    private fun toolsCallResult(id: String, request: JsonObject): String {
        val params = request.getAsJsonObject("params") ?: return errorJson(id, -32602, "missing params")
        val name = params.get("name")?.asString.orEmpty()
        val args = params.getAsJsonObject("arguments") ?: JsonObject()
        
        return try {
            val result = toolRegistry.execute(name, args)
                ?: return errorJson(id, -32601, "unknown tool: $name")
            resultJson(id, result)
        } catch (e: Exception) {
            errorJson(id, -32603, e.message ?: "internal error")
        }
    }

    private fun toolsListResult(id: String): String =
        resultJson(id, JsonObject().apply { add("tools", GeminiMcpTools.list()) })

    private fun initializeResult(id: String): String =
        resultJson(id, JsonObject().apply {
            addProperty("protocolVersion", "2024-11-05")
            add("capabilities", JsonObject().apply {
                add("tools", JsonObject())
            })
            add("serverInfo", JsonObject().apply {
                addProperty("name", "xed-ide-bridge")
                addProperty("version", "1.0.0")
            })
        })

    private fun ideContextJson(): String {
        val context = JsonObject().apply {
            add("workspaceState", JsonObject().apply {
                addProperty("isTrusted", true)
                add("openFiles", JsonArray().apply {
                    ideService.getOpenFiles().forEach { add(it) }
                })
            })
        }
        return gson.toJson(context)
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val queryToken = session.parameters["token"]?.firstOrNull()
        val headerToken = session.headers["authorization"]?.removePrefix("Bearer ")
        return queryToken == token || headerToken == token
    }

    private fun hasValidHost(session: IHTTPSession): Boolean {
        val host = session.headers["host"] ?: return true
        val hostname = host.substringBefore(":")
        return hostname == "127.0.0.1" || hostname == "localhost" || hostname == "[::1]"
    }

    private fun resultJson(id: String, result: JsonObject): String =
        JsonObject().apply { 
            addProperty("jsonrpc", "2.0")
            add("id", if (id == "null") JsonNull.INSTANCE else JsonParser.parseString(id))
            add("result", result) 
        }.let { gson.toJson(it) }

    private fun errorJson(id: String?, code: Int, message: String): String =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", if (id == null || id == "null") JsonNull.INSTANCE else JsonParser.parseString(id))
            add("error", JsonObject().apply { addProperty("code", code); addProperty("message", message) })
        }.let { gson.toJson(it) }

    private fun notificationJson(method: String, params: JsonObject): String =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", method)
            add("params", params)
        }.let { gson.toJson(it) }

    private fun readRequestBodyUtf8(session: IHTTPSession): Result<String> =
        runCatching {
            val contentLength = (session.headers["content-length"] ?: session.headers["Content-Length"])?.toIntOrNull()
            if (contentLength != null && contentLength > 0) {
                val bytes = ByteArray(contentLength)
                var offset = 0
                while (offset < contentLength) {
                    val read = session.inputStream.read(bytes, offset, contentLength - offset)
                    if (read <= 0) break
                    offset += read
                }
                String(bytes, 0, offset, Charsets.UTF_8)
            } else {
                val body = mutableMapOf<String, String>()
                session.parseBody(body)
                body["postData"].orEmpty()
            }
        }

    private fun json(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body).apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Cache-Control", "no-store")
        }
}
