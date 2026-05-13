package com.rk.ai.bridge.server

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.bridge.tools.*
import com.rk.ai.bridge.IdeNotificationSender
import com.rk.ai.service.IdeService
import com.rk.xededitor.BuildConfig
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.util.UUID
import java.util.concurrent.Semaphore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class IdeBridgeServer(
    requestedPort: Int,
    private val token: String,
    initialIdeService: IdeService
) : NanoHTTPD(requestedPort), IdeNotificationSender {

    override fun sendNotification(method: String, params: JsonObject) = sseManager.sendNotification(method, params)

    val port: Int get() = listeningPort
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var toolRegistry = McpToolRegistry(initialIdeService)
    private val mcpDispatcher = McpDispatcher { toolRegistry }
    private val httpSessionTracker = HttpSessionTracker { connectedClients = it }
    private val sseManager = SseManager(mcpDispatcher, { ideContextJson() }, { httpSessionTracker.updateSseCount(it) }, serverScope)

    @Volatile var connectedClients: Int = 0; private set
    val toolsCount: Int get() = toolRegistry.listSchemas().size()
    @Volatile private var activeMcpSessionId: String? = null

    var ideService: IdeService = initialIdeService
        set(value) {
            field = value
            toolRegistry = McpToolRegistry(value).also { registerTools(it) }
        }

    companion object {
        private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
        private const val MAX_CONCURRENT_TOOL_CALLS = 8
    }

    private val toolExecutionPermits = Semaphore(MAX_CONCURRENT_TOOL_CALLS)

    init {
        registerTools(toolRegistry)
        httpSessionTracker.startBackgroundCleanup(serverScope)
    }

    override fun stop() {
        serverScope.cancel()
        super.stop()
    }

    private fun registerTools(registry: McpToolRegistry) {
        registry.apply {
            register(GetIdeInfoTool()); register(GetGuidelinesTool()); register(ReadFileTool())
            register(ReadFilesTool()); register(WriteFileTool()); register(ListFilesTool())
            register(GetOpenFilesTool()); register(GetActiveFileTool()); register(GetSelectionTool())
            register(ReplaceSelectionTool()); register(InsertAtCursorTool()); register(SaveOpenFilesTool())
            register(RefreshOpenEditorsTool()); register(RefreshFileTool()); register(OpenDiffTool())
            register(GetDiffResultTool()); register(RejectDiffTool()); register(RunCommandTool())
            register(ShowMessageTool()); register(SearchCodeTool()); register(SearchSymbolsTool())
            register(FindFilesTool()); register(GetDiagnosticsTool()); register(FindDefinitionsTool())
            register(RenameSymbolTool()); register(FormatDocumentTool()); register(GetGitStatusTool())
            register(CreateFileTool()); register(DeleteFileTool()); register(RenameFileTool())
            register(ApplyBatchEditsTool()); register(GetTerminalOutputTool()); register(GetProjectStructureTool())
            register(GetProjectSummaryTool()); register(GetSymbolUnderCursorTool())
            register(GetProjectConfigTool()); register(GetGitDiffTool())
        }
    }

    override fun serve(session: IHTTPSession): Response {
        d("serve ${session.method} ${session.uri}")
        val rawPostBody = if (session.method == Method.POST) {
            readRequestBodyUtf8(session).getOrElse {
                return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, it.message ?: "invalid request"))
            }
        } else null
        if (!hasValidHost(session)) return json(Response.Status.FORBIDDEN, errorJson(null, -32003, "invalid host"))
        if (session.uri != "/health" && session.uri != "/debug" && !isAuthorized(session))
            return json(Response.Status.UNAUTHORIZED, errorJson(null, -32001, "unauthorized"))
        return when (session.uri) {
            "/health" -> json(Response.Status.OK, "{\"ok\":true}")
            "/context" -> json(Response.Status.OK, ideContextJson())
            "/mcp-info" -> json(Response.Status.OK, bridgeInfoJson())
            "/debug" -> json(Response.Status.OK, debugJson())
            "/refresh" -> { ideService.refreshEditors(); json(Response.Status.OK, "{\"ok\":true}") }
            "/sse" -> sseManager.createSseStream(session)
            "/mcp" -> {
                toolExecutionPermits.acquireUninterruptibly()
                try {
                    handleMcp(session, rawPostBody)
                } finally {
                    toolExecutionPermits.release()
                }
            }
            "/messages" -> handleMessages(session, rawPostBody)
            "/external-editor" -> handleExternalEditor(session, rawPostBody)
            else -> json(Response.Status.NOT_FOUND, errorJson(null, -32601, "not_found"))
        }
    }

    private fun handleMcp(session: IHTTPSession, rawPostBody: String?): Response {
        if (session.method == Method.GET) return sseManager.createMcpStream(
            resolveMcpSessionId("initialize", null) ?: "default"
        )
        if (session.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, errorJson(null, -32601, "method_not_allowed"))
        val request = parseJsonRequest(rawPostBody) ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "parse error"))
        val id = request.get("id") ?: JsonNull.INSTANCE
        val method = request.get("method")?.asString.orEmpty()
        val responseSessionId = resolveMcpSessionId(method, session.headers[MCP_SESSION_ID_HEADER]?.takeIf { it.isNotBlank() })
        return json(Response.Status.OK, mcpDispatcher.dispatch(id, method, request)).apply {
            responseSessionId?.let { addHeader(MCP_SESSION_ID_HEADER, it) }
        }
    }

    private fun handleMessages(session: IHTTPSession, rawPostBody: String?): Response {
        if (session.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, errorJson(null, -32601, "method_not_allowed"))
        val request = parseJsonRequest(rawPostBody) ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "parse error"))
        val method = request.get("method")?.asString.orEmpty()
        val requestedSessionId = session.parameters["sessionId"]?.firstOrNull() ?: session.headers[MCP_SESSION_ID_HEADER]
        val sessionId = resolveMcpSessionId(method, requestedSessionId) ?: "default"
        val id = request.get("id") ?: JsonNull.INSTANCE
        val responseBody = mcpDispatcher.dispatch(id, method, request)
        if (sseManager.pushToSession(sessionId, responseBody)) {
            return json(Response.Status.OK, mcpDispatcher.resultJson(id, JsonObject().apply { addProperty("_ack", true) }))
        }
        return json(Response.Status.OK, responseBody).apply { addHeader(MCP_SESSION_ID_HEADER, sessionId) }
    }

    private fun resolveMcpSessionId(method: String, requestedSessionId: String?): String? {
        if (!requestedSessionId.isNullOrBlank()) { activeMcpSessionId = requestedSessionId; return requestedSessionId }
        if (method == "initialize") {
            val newId = httpSessionTracker.createSession()
            activeMcpSessionId = newId; return newId
        }
        activeMcpSessionId?.let { httpSessionTracker.touchSession(it) }
        return activeMcpSessionId
    }

    private fun handleExternalEditor(session: IHTTPSession, rawPostBody: String?): Response {
        if (session.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, errorJson(null, -32601, "method_not_allowed"))
        val request = parseJsonRequest(rawPostBody) ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "parse error"))
        val newPath = request.get("newPath")?.asString.orEmpty()
        if (newPath.isBlank()) return json(Response.Status.BAD_REQUEST, errorJson(null, -32602, "newPath required"))
        val oldPath = request.get("oldPath")?.asString?.takeIf { it.isNotBlank() }
        val newFile = ideService.resolvePath(newPath) ?: File(newPath)
        val oldFile = oldPath?.let { ideService.resolvePath(it) ?: File(it) }
        val targetFile = oldFile ?: newFile
        val oldContent = oldFile?.let { runCatching { it.readText() }.getOrDefault("") }
            ?: runBlocking(Dispatchers.IO) { ideService.getFileContent(targetFile.absolutePath) }.orEmpty()
        val newContent = runCatching { newFile.readText() }.getOrElse {
            return json(Response.Status.BAD_REQUEST, errorJson(null, -32602, it.message ?: "cannot read newPath"))
        }
        ideService.showPatch(targetFile.absolutePath, oldContent, newContent, "Review Gemini editor change") {
            runBlocking(Dispatchers.IO) { ideService.writeFile(targetFile, newContent); ideService.refreshEditors(targetFile.absolutePath, force = false) }
        }
        return json(Response.Status.OK, JsonObject().apply { addProperty("message", "Review opened in Xed Editor for ${targetFile.absolutePath}") }.let { gson.toJson(it) })
    }

    private fun bridgeInfoJson(): String = gson.toJson(JsonObject().apply {
        addProperty("name", "xed-ide-bridge"); addProperty("version", "1.0.0"); addProperty("protocol", "mcp")
        addProperty("tools", toolRegistry.listSchemas().size()); addProperty("clients", connectedClients)
        addProperty("sseClients", httpSessionTracker.sseSessionCount)
    })

    private fun ideContextJson(): String = gson.toJson(JsonObject().apply {
        add("workspaceState", JsonObject().apply {
            addProperty("isTrusted", true)
            add("openFiles", com.google.gson.JsonArray().apply {
                runBlocking(Dispatchers.IO) { ideService.getOpenFiles() }.forEach { add(it) }
            })
        })
    })

    private fun debugJson(): String = gson.toJson(JsonObject().apply {
        addProperty("port", listeningPort); addProperty("clients", connectedClients)
        addProperty("sseClients", httpSessionTracker.sseSessionCount)
        addProperty("httpClients", httpSessionTracker.httpSessionCount)
        addProperty("tools", toolRegistry.listSchemas().size()); addProperty("tokenPrefix", token.take(8))
        add("activeSessionId", if (activeMcpSessionId != null) JsonObject().apply { addProperty("id", activeMcpSessionId) } else JsonNull.INSTANCE)
    })

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val queryToken = session.parameters["token"]?.firstOrNull()
        val auth = session.headers["authorization"]?.trim().orEmpty()
        val headerToken = auth.split(' ', limit = 2).takeIf { it.size == 2 && it[0].equals("Bearer", ignoreCase = true) }?.get(1)
        return queryToken == token || headerToken == token
    }

    private fun hasValidHost(session: IHTTPSession): Boolean {
        val host = session.headers["host"]?.substringBefore(":") ?: return true
        return host == "127.0.0.1" || host == "localhost" || host == "[::1]"
    }

    private fun parseJsonRequest(raw: String?): JsonObject? = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()

    private fun errorJson(id: JsonElement?, code: Int, message: String): String = mcpDispatcher.errorJson(id, code, message)

    private fun readRequestBodyUtf8(session: IHTTPSession): Result<String> = runCatching {
        val contentLength = (session.headers["content-length"] ?: session.headers["Content-Length"])?.toIntOrNull()
        if (contentLength != null && contentLength > 0) {
            val bytes = ByteArray(contentLength); var offset = 0
            while (offset < contentLength) { val read = session.inputStream.read(bytes, offset, contentLength - offset); if (read <= 0) break; offset += read }
            String(bytes, 0, offset, Charsets.UTF_8)
        } else {
            val body = mutableMapOf<String, String>(); session.parseBody(body); body["postData"].orEmpty()
        }
    }

    private fun json(status: Response.Status, body: String): Response =
        NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", body).apply {
            addHeader("Access-Control-Allow-Origin", "*"); addHeader("Cache-Control", "no-store")
        }

    private fun d(msg: String) { if (BuildConfig.DEBUG) Log.d("IdeBridgeServer", msg) }
}
