package com.rk.ai.bridge.server

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.bridge.tools.*
import com.rk.ai.service.IdeNotificationSender
import com.rk.ai.service.IdeService
import com.rk.xededitor.BuildConfig
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class IdeBridgeServer(
    requestedPort: Int,
    private val token: String,
    initialIdeService: IdeService
) : NanoHTTPD(requestedPort), IdeNotificationSender {

    override fun sendNotification(method: String, params: JsonObject) = sseManager.sendNotification(method, params)

    val port: Int get() = listeningPort
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var toolRegistry = McpToolRegistry()
    private val httpSessionTracker = HttpSessionTracker { connectedClients = it }
    private val mcpDispatcher = McpDispatcher(
        toolRegistry = { toolRegistry },
        ideService = { ideService },
        serverScope = serverScope,
    )
    private val sseManager = SseManager(mcpDispatcher, { gson.toJson(currentIdeContext()) }, { httpSessionTracker.updateSseCount(it) }, serverScope)

    @Volatile var connectedClients: Int = 0; private set
    val toolsCount: Int get() = toolRegistry.listSchemas().size()

    var ideService: IdeService = initialIdeService
        set(value) {
            field = value
            toolRegistry = McpToolRegistry().also { registerTools(it) }
        }

    companion object {
        private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
        private const val MAX_CONCURRENT_TOOL_CALLS = 8
    }

    private val toolConcurrencyLimit = Semaphore(MAX_CONCURRENT_TOOL_CALLS)

    init {
        mcpDispatcher.sendNotification = { sessionId, method, params -> sseManager.pushToSession(sessionId, mcpDispatcher.notificationJson(method, params)) }
        registerTools(toolRegistry)
        httpSessionTracker.startBackgroundCleanup(serverScope)
    }

    override fun stop() {
        serverScope.cancel()
        super.stop()
    }

    private fun registerTools(registry: McpToolRegistry) {
        registry.apply {
            register(GetIdeInfoTool()); register(GetGuidelinesTool())
            register(ReadFileTool()); register(CatTool())
            register(ReadFilesTool()); register(WriteFileTool())
            register(ListFilesTool()); register(LsTool())
            register(OpenFileTool())
            register(GetOpenFilesTool()); register(GetActiveFileTool())
            register(GetSelectionTool()); register(ReplaceSelectionTool()); register(InsertAtCursorTool())
            register(SaveOpenFilesTool()); register(RefreshOpenEditorsTool()); register(RefreshFileTool())
            register(OpenDiffTool()); register(GetDiffResultTool()); register(RejectDiffTool())
            register(RunCommandTool()); register(ShowMessageTool())
            register(GetEnvironmentTool()); register(GetClipboardTool()); register(WriteToClipboardTool())
            register(SearchCodeTool()); register(GrepTool()); register(GrepSearchTool())
            register(SearchSymbolsTool())
            register(FindFilesTool()); register(GlobTool())
            register(HeadTool()); register(TailTool()); register(WcTool())
            register(CountLinesTool()); register(StatTool())
            register(GetDiagnosticsTool()); register(FindDefinitionsTool()); register(FindReferencesTool())
            register(RenameSymbolTool()); register(FormatDocumentTool())
            register(GetGitStatusTool()); register(GetGitDiffTool()); register(GitCommitTool()); register(GitCheckoutTool())
            register(CreateFileTool()); register(DeleteFileTool()); register(RenameFileTool()); register(MoveFileTool())
            register(CreateDirectoryTool()); register(MkdirTool())
            register(ApplyBatchEditsTool()); register(EditFileTool())
            register(GetTerminalOutputTool())
            register(GetProjectStructureTool()); register(GetProjectSummaryTool())
            register(GetSymbolUnderCursorTool()); register(GetProjectConfigTool())
            // New web tools
            register(WebFetchTool()); register(WebSearchTool()); register(WebDownloadTool()); register(WebResearchTool())
            // New GitHub tools
            register(GitHubRepoInfoTool()); register(GitHubReadmeTool())
            register(GitHubSearchCodeTool()); register(GitHubFileFetchTool())
            // New package tools
            register(NpmSearchTool()); register(PipSearchTool()); register(MavenSearchTool())
        }
    }

    override fun serve(session: IHTTPSession): Response {
        d("serve ${session.method} ${session.uri}")
        if (session.method == Method.OPTIONS) return options()
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
            "/context" -> json(Response.Status.OK, ideContextJsonSync())
            "/mcp-info" -> json(Response.Status.OK, bridgeInfoJson())
            "/debug" -> json(Response.Status.OK, debugJson())
            "/refresh" -> { ideService.refreshEditors(); json(Response.Status.OK, "{\"ok\":true}") }
            "/sse" -> sseManager.createSseStream(session)
            "/mcp" -> runBlockingSafely { handleMcp(session, rawPostBody) }
            "/messages" -> runBlockingSafely { handleMessages(session, rawPostBody) }
            "/external-editor" -> runBlockingSafely { handleExternalEditor(session, rawPostBody) }
            else -> json(Response.Status.NOT_FOUND, errorJson(null, -32601, "not_found"))
        }
    }

    private fun runBlockingSafely(block: suspend () -> Response): Response {
        return try {
            runBlocking(Dispatchers.IO) { block() }
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, errorJson(null, -32603, "${e::class.java.simpleName}: ${e.message ?: "internal error"}"))
        }
    }

    private suspend fun <T> withConcurrencyLimit(block: suspend CoroutineScope.() -> T): T =
        coroutineScope { toolConcurrencyLimit.withPermit { block() } }

    private suspend fun handleMcp(session: IHTTPSession, rawPostBody: String?): Response {
        val requestedSessionId = session.headers[MCP_SESSION_ID_HEADER]?.takeIf { it.isNotBlank() }

        if (session.method == Method.GET) {
            val sessionId = requestedSessionId ?: httpSessionTracker.createSession()
            httpSessionTracker.touchSession(sessionId)
            return sseManager.createMcpStream(sessionId).apply { addHeader(MCP_SESSION_ID_HEADER, sessionId) }
        }
        if (session.method == Method.DELETE) {
            val sessionId = requestedSessionId
            if (sessionId != null) {
                httpSessionTracker.removeSession(sessionId)
                sseManager.closeSession(sessionId)
            }
            return accepted()
        }
        if (session.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, errorJson(null, -32601, "method_not_allowed"))
        
        val request = parseJsonRequest(rawPostBody) ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "parse error"))
        val method = request.get("method")?.asString.orEmpty()
        val sessionId = resolveMcpSessionId(method, requestedSessionId) ?: "default"
        httpSessionTracker.touchSession(sessionId)
        val id = request.get("id") ?: JsonNull.INSTANCE
        if (isNotification(request, method)) {
            handleNotification(sessionId, method)
            return accepted().apply { addHeader(MCP_SESSION_ID_HEADER, sessionId) }
        }
        
        val result = withConcurrencyLimit {
            mcpDispatcher.dispatch(sessionId, id, method, request)
        }
        return json(Response.Status.OK, result).apply {
            addHeader(MCP_SESSION_ID_HEADER, sessionId)
        }
    }

    private suspend fun handleMessages(session: IHTTPSession, rawPostBody: String?): Response {
        if (session.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, errorJson(null, -32601, "method_not_allowed"))
        val request = parseJsonRequest(rawPostBody) ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "parse error"))
        val method = request.get("method")?.asString.orEmpty()
        val requestedSessionId = session.parameters["sessionId"]?.firstOrNull() ?: session.headers[MCP_SESSION_ID_HEADER]
        val sessionId = resolveMcpSessionId(method, requestedSessionId) ?: "default"
        httpSessionTracker.touchSession(sessionId)
        val id = request.get("id") ?: JsonNull.INSTANCE
        if (isNotification(request, method)) {
            handleNotification(sessionId, method)
            return accepted().apply { addHeader(MCP_SESSION_ID_HEADER, sessionId) }
        }
        val responseBody = withConcurrencyLimit {
            mcpDispatcher.dispatch(sessionId, id, method, request)
        }
        if (sseManager.pushToSession(sessionId, responseBody)) {
            return json(Response.Status.OK, mcpDispatcher.resultJson(id, JsonObject().apply { addProperty("_ack", true) }))
        }
        return json(Response.Status.OK, responseBody).apply { addHeader(MCP_SESSION_ID_HEADER, sessionId) }
    }

    private fun resolveMcpSessionId(method: String, requestedSessionId: String?): String? {
        if (!requestedSessionId.isNullOrBlank()) { return requestedSessionId }
        if (method == "initialize") {
            return httpSessionTracker.createSession()
        }
        return null
    }

    private fun isNotification(request: JsonObject, method: String): Boolean =
        !request.has("id") || request.get("id").isJsonNull || method.startsWith("notifications/")

    private fun handleNotification(sessionId: String, method: String) {
        when (method) {
            "notifications/initialized", "initialized" -> Unit
            "notifications/cancelled", "notifications/progress", "notifications/roots/list_changed" -> Unit
            else -> d("ignored MCP notification: $method for session $sessionId")
        }
    }


    private suspend fun handleExternalEditor(session: IHTTPSession, rawPostBody: String?): Response {
        if (session.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, errorJson(null, -32601, "method_not_allowed"))
        val request = parseJsonRequest(rawPostBody) ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "parse error"))
        val newPath = request.get("newPath")?.asString.orEmpty()
        if (newPath.isBlank()) return json(Response.Status.BAD_REQUEST, errorJson(null, -32602, "newPath required"))
        val oldPath = request.get("oldPath")?.asString?.takeIf { it.isNotBlank() }
        val newFile = ideService.resolvePath(newPath) ?: File(newPath)
        val oldFile = oldPath?.let { ideService.resolvePath(it) ?: File(it) }
        val targetFile = oldFile ?: newFile
        val oldContent = oldFile?.let { runCatching { it.readText() }.getOrDefault("") }
            ?: withContext(Dispatchers.IO) { ideService.getFileContent(targetFile.absolutePath) }.orEmpty()
        val newContent = runCatching { newFile.readText() }.getOrElse {
            return json(Response.Status.BAD_REQUEST, errorJson(null, -32602, it.message ?: "cannot read newPath"))
        }
        ideService.showPatch(targetFile.absolutePath, oldContent, newContent, "Review Gemini editor change") {
            serverScope.launch {
                ideService.writeFile(targetFile, newContent)
                ideService.refreshEditors(targetFile.absolutePath, force = false)
            }
        }
        return json(Response.Status.OK, JsonObject().apply { addProperty("message", "Review opened in Xed Editor for ${targetFile.absolutePath}") }.let { gson.toJson(it) })
    }

    private fun bridgeInfoJson(): String = gson.toJson(JsonObject().apply {
        addProperty("name", "xed-ide-bridge"); addProperty("version", "2.1.0"); addProperty("protocol", "mcp")
        addProperty("tools", toolRegistry.listSchemas().size()); addProperty("clients", connectedClients)
        addProperty("sseClients", httpSessionTracker.sseSessionCount)
    })

    private fun currentIdeContext(): JsonObject = JsonObject().apply {
        add("workspaceState", JsonObject().apply {
            addProperty("isTrusted", true)
            add("openFiles", com.google.gson.JsonArray().apply {
                runBlocking(Dispatchers.IO) { ideService.getOpenFiles() }.forEach { add(it) }
            })
        })
    }

    private fun ideContextJsonSync(): String = gson.toJson(currentIdeContext())

    private fun debugJson(): String = gson.toJson(JsonObject().apply {
        addProperty("port", listeningPort); addProperty("clients", connectedClients)
        addProperty("sseClients", httpSessionTracker.sseSessionCount)
        addProperty("httpClients", httpSessionTracker.httpSessionCount)
        addProperty("tools", toolRegistry.listSchemas().size()); addProperty("tokenPrefix", token.take(8))
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
        val headers = session.headers
        val contentLength = headers.entries.firstOrNull { (k, _) -> k.equals("content-length", ignoreCase = true) }?.value?.toIntOrNull()
        if (contentLength != null && contentLength > 0) {
            val bytes = ByteArray(contentLength); var offset = 0
            while (offset < contentLength) { val read = session.inputStream.read(bytes, offset, contentLength - offset); if (read <= 0) break; offset += read }
            String(bytes, 0, offset, Charsets.UTF_8)
        } else {
            val body = mutableMapOf<String, String>(); session.parseBody(body); body["postData"].orEmpty()
        }
    }

    private fun json(status: Response.Status, body: String): Response =
        NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", body).apply { addCommonHeaders() }

    private fun accepted(): Response =
        NanoHTTPD.newFixedLengthResponse(Response.Status.ACCEPTED, "text/plain; charset=utf-8", "").apply { addCommonHeaders() }

    private fun options(): Response =
        NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", "").apply { addCommonHeaders() }

    private fun Response.addCommonHeaders() {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, MCP-Session-Id, mcp-session-id")
        addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        addHeader("Access-Control-Expose-Headers", "MCP-Session-Id, mcp-session-id, MCP-Protocol-Version")
        addHeader("MCP-Protocol-Version", "2025-03-26")
        addHeader("Cache-Control", "no-store")
    }

    private fun d(msg: String) { if (BuildConfig.DEBUG) Log.d("IdeBridgeServer", msg) }
}
