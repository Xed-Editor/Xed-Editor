package com.rk.ai.bridge.server

import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
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
import java.util.UUID
import kotlinx.coroutines.runBlocking

class GeminiBridgeServer(
    requestedPort: Int,
    private val token: String,
    initialIdeService: GeminiIdeService
) : NanoHTTPD(requestedPort), GeminiNotificationSender {

    val port: Int get() = listeningPort

    companion object {
        private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private var toolRegistry = McpToolRegistry(initialIdeService)
    
    var ideService: GeminiIdeService = initialIdeService
        set(value) {
            field = value
            val newRegistry = McpToolRegistry(value)
            registerTools(newRegistry)
            toolRegistry = newRegistry
        }

    init {
        registerTools(toolRegistry)
    }

    private fun registerTools(registry: McpToolRegistry) {
        registry.apply {
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
            register(RejectDiffTool())
            register(RunCommandTool())
            register(ShowMessageTool())
            register(SearchCodeTool())
            register(FindFilesTool())
            register(GetDiagnosticsTool())
            register(FindDefinitionsTool())
            register(FindReferencesTool())
            register(RenameSymbolTool())
            register(FormatDocumentTool())
            register(GetGitStatusTool())
            register(CreateFileTool())
            register(DeleteFileTool())
            register(GetTerminalOutputTool())
            register(GetProjectStructureTool())
        }
    }

    private val sseClients = ConcurrentHashMap<String, PrintWriter>()
    private val sseLock = Any()
    @Volatile private var activeMcpSessionId: String? = null

    private fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d("GeminiBridgeServer", msg)
    }

    override fun serve(session: IHTTPSession): Response {
        d("serve ${session.method} ${session.uri} params=${session.parameters.keys} headers=${session.headers.keys}")

        val rawPostBody =
            if (session.method == Method.POST) {
                readRequestBodyUtf8(session).getOrElse {
                    return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, it.message ?: "invalid request"))
                }.also { d("POST body length=${it.length}") }
            } else {
                null
            }

        if (!hasValidHost(session)) {
            d("Invalid host: ${session.headers["host"]}")
            return json(Response.Status.FORBIDDEN, errorJson(null, -32003, "invalid host"))
        }
        if (!isAuthorized(session)) {
            d("Unauthorized: queryToken=${session.parameters["token"]?.firstOrNull()?.take(4)}... header=${session.headers["authorization"]?.take(10)}...")
            return json(Response.Status.UNAUTHORIZED, errorJson(null, -32001, "unauthorized"))
        }

        return when (session.uri) {
            "/health" -> json(Response.Status.OK, "{\"ok\":true}")
            "/context" -> json(Response.Status.OK, ideContextJson())
            "/refresh" -> {
                ideService.refreshEditors()
                json(Response.Status.OK, "{\"ok\":true}")
            }
            "/external-editor" -> serveExternalEditor(session, rawPostBody)
            "/mcp" -> serveMcp(session, rawPostBody)
            else -> json(Response.Status.NOT_FOUND, errorJson(null, -32601, "not_found"))
        }
    }

    private fun serveExternalEditor(session: IHTTPSession, rawPostBody: String?): Response {
        if (session.method != Method.POST) {
            return json(Response.Status.METHOD_NOT_ALLOWED, errorJson(null, -32601, "method_not_allowed"))
        }

        val raw = rawPostBody
            ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "invalid request"))
        val request = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "parse error"))

        val newPath = request.get("newPath")?.asString.orEmpty()
        if (newPath.isBlank()) {
            return json(Response.Status.BAD_REQUEST, errorJson(null, -32602, "newPath required"))
        }

        val oldPath = request.get("oldPath")?.asString?.takeIf { it.isNotBlank() }
        val newFile = ideService.resolvePath(newPath) ?: File(newPath)
        val oldFile = oldPath?.let { ideService.resolvePath(it) ?: File(it) }
        val targetFile = oldFile ?: newFile
        val oldContent = oldFile?.let { runCatching { it.readText() }.getOrDefault("") }
            ?: runBlocking { ideService.getFileContent(targetFile.absolutePath) }.orEmpty()
        val newContent = runCatching { newFile.readText() }.getOrElse {
            return json(Response.Status.BAD_REQUEST, errorJson(null, -32602, it.message ?: "cannot read newPath"))
        }

        ideService.showPatch(targetFile.absolutePath, oldContent, newContent, "Review Gemini editor change") {
            runBlocking {
                ideService.writeFile(targetFile, newContent)
                ideService.refreshEditors(targetFile.absolutePath, force = false)
            }
        }

        return json(Response.Status.OK, JsonObject().apply { addProperty("message", "Review opened in Xed Editor for ${targetFile.absolutePath}") }.let { gson.toJson(it) })
    }

    private fun serveMcp(session: IHTTPSession, rawPostBody: String?): Response {
        if (session.method == Method.GET) {
            return serveMcpStream(session)
        }
        if (session.method != Method.POST) {
            return json(Response.Status.METHOD_NOT_ALLOWED, errorJson(null, -32601, "method_not_allowed"))
        }

        val raw = rawPostBody
            ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "invalid request"))

        val request = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "parse error"))

        val id = request.get("id") ?: JsonNull.INSTANCE
        val method = request.get("method")?.asString.orEmpty()
        val requestedSessionId = session.headers[MCP_SESSION_ID_HEADER]?.takeIf { it.isNotBlank() }
        val responseSessionId = resolveMcpSessionId(method, requestedSessionId)

        val responseBody =
            when (method) {
                "initialize" -> initializeResult(id, request)
                "tools/list" -> toolsListResult(id)
                "tools/call" -> toolsCallResult(id, request)
                "notifications/initialized" -> resultJson(id, JsonObject())
                else -> errorJson(id, -32601, "method not found: $method")
            }

        return json(Response.Status.OK, responseBody).apply {
            responseSessionId?.let { addHeader(MCP_SESSION_ID_HEADER, it) }
        }
    }

    private fun resolveMcpSessionId(method: String, requestedSessionId: String?): String? {
        if (!requestedSessionId.isNullOrBlank()) {
            activeMcpSessionId = requestedSessionId
            return requestedSessionId
        }
        if (method == "initialize") {
            val newSessionId = activeMcpSessionId ?: UUID.randomUUID().toString()
            activeMcpSessionId = newSessionId
            return newSessionId
        }
        return activeMcpSessionId
    }

    private fun serveMcpStream(session: IHTTPSession): Response {
        val requestedSessionId =
            session.headers[MCP_SESSION_ID_HEADER]?.takeIf { it.isNotBlank() }
                ?: session.parameters["sessionId"]?.firstOrNull()
                ?: activeMcpSessionId
                ?: "default"
        val output = PipedOutputStream()
        val input = PipedInputStream(output)
        val writer = PrintWriter(output)
synchronized(sseLock) {
    sseClients[requestedSessionId] = writer
}

// Notify IDE context on connection
synchronized(sseLock) {
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
        synchronized(sseLock) {
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
    }

    private fun toolsCallResult(id: JsonElement, request: JsonObject): String {
        val params = request.getAsJsonObject("params") ?: return errorJson(id, -32602, "missing params")
        val name = params.get("name")?.asString.orEmpty()
        val args = params.getAsJsonObject("arguments") ?: JsonObject()
        
        return try {
            val result = runBlocking {
                toolRegistry.execute(name, args)
            } ?: return errorJson(id, -32601, "unknown tool: $name")
            resultJson(id, result)
        } catch (e: Exception) {
            errorJson(id, -32603, e.message ?: "internal error")
        }
    }

    private fun toolsListResult(id: JsonElement): String =
        resultJson(id, JsonObject().apply { add("tools", GeminiMcpTools.list()) })

    private fun initializeResult(id: JsonElement, request: JsonObject): String =
        resultJson(id, JsonObject().apply {
            val negotiatedProtocol =
                request.getAsJsonObject("params")
                    ?.get("protocolVersion")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.takeIf { it.isNotBlank() }
                    ?: "2025-03-26"
            addProperty("protocolVersion", negotiatedProtocol)
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
                    runBlocking { ideService.getOpenFiles() }.forEach { add(it) }
                })
            })
        }
        return gson.toJson(context)
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val queryToken = session.parameters["token"]?.firstOrNull()
        val authorization = session.headers["authorization"]?.trim().orEmpty()
        val headerToken = authorization
            .split(' ', limit = 2)
            .takeIf { it.size == 2 && it[0].equals("Bearer", ignoreCase = true) }
            ?.get(1)
        return queryToken == token || headerToken == token
    }

    private fun hasValidHost(session: IHTTPSession): Boolean {
        val host = session.headers["host"] ?: return true
        val hostname = host.substringBefore(":")
        return hostname == "127.0.0.1" || hostname == "localhost" || hostname == "[::1]"
    }

    private fun resultJson(id: JsonElement?, result: JsonObject): String =
        JsonObject().apply { 
            addProperty("jsonrpc", "2.0")
            add("id", id ?: JsonNull.INSTANCE)
            add("result", result) 
        }.let { gson.toJson(it) }

    private fun errorJson(id: JsonElement?, code: Int, message: String): String =
        JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id ?: JsonNull.INSTANCE)
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
