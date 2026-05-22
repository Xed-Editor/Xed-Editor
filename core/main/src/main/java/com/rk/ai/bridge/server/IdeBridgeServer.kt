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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class IdeBridgeServer(
    requestedPort: Int,
    private val token: String,
    initialIdeService: IdeService
) : NanoHTTPD(requestedPort), IdeNotificationSender {

    override fun sendNotification(method: String, params: JsonObject) = sseManager.sendNotification(method, params)

    val port: Int get() = listeningPort
    private val gson = GsonBuilder().create()
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var toolRegistry = McpToolRegistry(initialIdeService)
    private val mcpDispatcher = McpDispatcher { toolRegistry }
    private val httpSessionTracker = HttpSessionTracker { connectedClients = it }
    private val sseManager = SseManager(mcpDispatcher, { contextJson() }, { httpSessionTracker.updateSseCount(it) }, serverScope, token, { listeningPort })

    @Volatile var connectedClients: Int = 0
        private set
    val toolsCount: Int get() = toolRegistry.listSchemas().size()

    var ideService: IdeService = initialIdeService
        set(value) {
            field = value
            toolRegistry = McpToolRegistry(value).also { registerTools(it) }
        }

    companion object {
        private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
        private const val MAX_CONCURRENT_TOOL_CALLS = 8
    }

    private val toolCallPermits = Semaphore(MAX_CONCURRENT_TOOL_CALLS)

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
            register(GetIdeInfoTool()); register(GetGuidelinesTool())
            register(ReadFileTool()); register(CatTool()); register(ReadFilesTool())
            register(WriteFileTool())
            register(ListFilesTool()); register(LsTool()); register(OpenFileTool())
            register(GetOpenFilesTool()); register(GetActiveFileTool())
            register(GetSelectionTool()); register(ReplaceSelectionTool()); register(InsertAtCursorTool())
            register(SaveOpenFilesTool()); register(RefreshOpenEditorsTool()); register(RefreshFileTool())
            register(OpenDiffTool()); register(GetDiffResultTool()); register(RejectDiffTool())
            register(RunCommandTool()); register(ShowMessageTool())
            register(SearchCodeTool()); register(GrepTool()); register(SearchSymbolsTool())
            register(FindFilesTool()); register(GlobTool())
            register(HeadTool()); register(TailTool()); register(WcTool())
            register(CountLinesTool()); register(StatTool())
            register(GetDiagnosticsTool()); register(FindDefinitionsTool()); register(FindReferencesTool())
            register(RenameSymbolTool()); register(FormatDocumentTool())
            register(WebSearchTool()); register(WebFetchTool()); register(WebScrapeTool())
            register(GetGitStatusTool()); register(GetGitDiffTool()); register(GitCommitTool()); register(GitCheckoutTool())
            register(GitLogTool()); register(ListGitBranchesTool()); register(GitPullTool()); register(GitPushTool())
            register(GitFetchTool()); register(GitCreateBranchTool()); register(GitStashTool()); register(GitStashPopTool())
            register(CreateFileTool()); register(DeleteFileTool()); register(RenameFileTool())
            register(ApplyBatchEditsTool())
            register(EditFileTool())
            register(GetTerminalOutputTool())
            register(GetProjectStructureTool()); register(GetProjectSummaryTool())
            register(GetSymbolUnderCursorTool()); register(GetProjectConfigTool())
            register(GetFileInfoTool())
            register(GetCodeFrameTool()); register(ReadProjectFilesTool())
            register(SearchAndReplaceTool())

            register(ListSessionsTool()); register(CreateTerminalTool()); register(KillTerminalTool())
            register(WriteToTerminalTool()); register(GetTerminalSessionOutputTool())
            register(GetClipboardTool()); register(SetClipboardTool())
            register(GetSettingTool()); register(SetSettingTool()); register(GetAllSettingsTool())
            register(CloseTabTool()); register(CloseOtherTabsTool())
            register(NavigateToTool())
            register(GetProblemsTool())
            register(GetFileTreeTool())
            register(GetGitBlameTool())
            register(FormatSelectionTool())
        }
    }

    override fun serve(session: IHTTPSession): Response {
        d("serve ${session.method} ${session.uri}")

        if (session.method == Method.OPTIONS) {
            return corsPreflightResponse()
        }

        val rawPostBody = if (session.method == Method.POST) {
            readRequestBodyUtf8(session).getOrElse {
                return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, it.message ?: "invalid request"))
            }
        } else null

        if (!hasValidHost(session)) {
            return json(Response.Status.FORBIDDEN, errorJson(null, -32003, "invalid host"))
        }

        val publicEndpoints = setOf("/health", "/debug")
        val mcpSseEndpoint = session.uri == "/mcp" && session.method == Method.GET
        val sseEndpoint = session.uri == "/sse"
        if (session.uri !in publicEndpoints && !mcpSseEndpoint && !sseEndpoint && !isAuthorized(session)) {
            return json(Response.Status.UNAUTHORIZED, errorJson(null, -32001, "unauthorized"))
        }

        return when (session.uri) {
            "/health" -> json(Response.Status.OK, "{\"ok\":true}")
            "/context" -> json(Response.Status.OK, contextJson())
            "/mcp-info" -> json(Response.Status.OK, bridgeInfoJson())
            "/debug" -> json(Response.Status.OK, debugJson())
            "/refresh" -> { ideService.refreshEditors(); json(Response.Status.OK, "{\"ok\":true}") }
            "/sse" -> sseManager.createSseStream(session)
            "/mcp" -> {
                if (session.method == Method.GET) {
                    sseManager.createMcpStream(session, resolveMcpSessionId("initialize", null) ?: "default")
                } else {
                    handleMcpRequest(session, rawPostBody)
                }
            }
            "/messages" -> handleMessagesRequest(session, rawPostBody)
            "/external-editor" -> handleExternalEditor(session, rawPostBody)
            else -> json(Response.Status.NOT_FOUND, errorJson(null, -32601, "not_found"))
        }
    }

    private fun handleMcpRequest(session: IHTTPSession, rawPostBody: String?): Response {
        if (session.method != Method.POST) {
            return json(Response.Status.METHOD_NOT_ALLOWED, errorJson(null, -32601, "method_not_allowed"))
        }
        val request = parseJsonRequest(rawPostBody) ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "parse error"))
        val method = request.get("method")?.asString.orEmpty()
        val id = request.get("id") ?: JsonNull.INSTANCE

        val responseSessionId = resolveMcpSessionId(method, session.headers[MCP_SESSION_ID_HEADER]?.takeIf { it.isNotBlank() })

        val responseBody = if (method == "tools/call") {
            toolCallPermits.acquireUninterruptibly()
            try {
                runBlocking { mcpDispatcher.dispatch(id, method, request) }
            } finally {
                toolCallPermits.release()
            }
        } else {
            runBlocking { mcpDispatcher.dispatch(id, method, request) }
        }

        return json(Response.Status.OK, responseBody).apply {
            responseSessionId?.let { addHeader(MCP_SESSION_ID_HEADER, it) }
        }
    }

    private fun handleMessagesRequest(session: IHTTPSession, rawPostBody: String?): Response {
        if (session.method != Method.POST) {
            return json(Response.Status.METHOD_NOT_ALLOWED, errorJson(null, -32601, "method_not_allowed"))
        }
        val request = parseJsonRequest(rawPostBody) ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "parse error"))
        val method = request.get("method")?.asString.orEmpty()
        val id = request.get("id") ?: JsonNull.INSTANCE

        val requestedSessionId = session.parameters["sessionId"]?.firstOrNull()
            ?: session.headers[MCP_SESSION_ID_HEADER]
        val sessionId = resolveMcpSessionId(method, requestedSessionId) ?: "default"

        val responseBody = if (method == "tools/call") {
            toolCallPermits.acquireUninterruptibly()
            try {
                runBlocking { mcpDispatcher.dispatch(id, method, request) }
            } finally {
                toolCallPermits.release()
            }
        } else {
            runBlocking { mcpDispatcher.dispatch(id, method, request) }
        }

        val pushed = sseManager.pushToSession(sessionId, responseBody)
        if (pushed) {
            return json(Response.Status.OK, mcpDispatcher.resultJson(id, JsonObject().apply { addProperty("_ack", true) }))
        }

        return json(Response.Status.OK, responseBody).apply { addHeader(MCP_SESSION_ID_HEADER, sessionId) }
    }

    private fun resolveMcpSessionId(method: String, requestedSessionId: String?): String? {
        if (!requestedSessionId.isNullOrBlank()) {
            httpSessionTracker.touchSession(requestedSessionId)
            return requestedSessionId
        }
        if (method == "initialize") {
            return UUID.randomUUID().toString()
        }
        return null
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
        val oldContent = runBlocking {
            withContext(Dispatchers.IO) {
                withTimeout(10_000L) {
                    oldFile?.let { runCatching { it.readText() }.getOrDefault("") }
                        ?: ideService.getFileContent(targetFile.absolutePath).orEmpty()
                }
            }
        }
        val newContent = runBlocking {
            withContext(Dispatchers.IO) {
                runCatching { withTimeout(10_000L) { newFile.readText() } }.getOrNull()
            }
        } ?: return json(Response.Status.BAD_REQUEST, errorJson(null, -32602, "cannot read newPath"))
        ideService.showPatch(targetFile.absolutePath, oldContent, newContent, "Review AI editor change") {
            serverScope.launch {
                runCatching { ideService.writeFile(targetFile, newContent); ideService.refreshEditors(targetFile.absolutePath, force = false) }
            }
        }
        return json(Response.Status.OK, JsonObject().apply { addProperty("message", "Review opened in Xed Editor for ${targetFile.absolutePath}") }.let { gson.toJson(it) })
    }

    private fun corsPreflightResponse(): Response =
        NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", "{}").apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, x-ide-token, mcp-session-id")
            addHeader("Access-Control-Max-Age", "86400")
            addHeader("Cache-Control", "no-store")
        }

    private fun bridgeInfoJson(): String = gson.toJson(JsonObject().apply {
        addProperty("name", "xed-ide-bridge"); addProperty("version", "1.0.0"); addProperty("protocol", "mcp")
        addProperty("tools", toolRegistry.listSchemas().size()); addProperty("clients", connectedClients)
        addProperty("sseClients", httpSessionTracker.sseSessionCount)
    })

    @Volatile private var contextCacheJson: String? = null
    @Volatile private var contextCacheTime: Long = 0L

    private fun contextJson(): String {
        val now = System.currentTimeMillis()
        val cached = contextCacheJson
        if (cached != null && now - contextCacheTime < 1000) return cached
        val json = gson.toJson(JsonObject().apply {
            add("workspaceState", JsonObject().apply {
                addProperty("isTrusted", true)
                add("openFiles", com.google.gson.JsonArray().apply {
                    runBlocking { ideService.getOpenFiles() }.forEach { add(it) }
                })
            })
        })
        contextCacheJson = json
        contextCacheTime = now
        return json
    }

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
        val xToken = session.headers["x-ide-token"]?.trim()
        return queryToken == token || headerToken == token || xToken == token
    }

    private fun hasValidHost(session: IHTTPSession): Boolean {
        val host = session.headers["host"]?.substringBefore(":") ?: return true
        return host == "127.0.0.1" || host == "localhost" || host == "[::1]"
    }

    private fun parseJsonRequest(raw: String?): JsonObject? = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()

    private fun errorJson(id: JsonElement?, code: Int, message: String): String = mcpDispatcher.errorJson(id, code, message)

    private fun readRequestBodyUtf8(session: IHTTPSession): Result<String> = runCatching {
        val headers = session.headers
        val contentLength = headers["content-length"]?.toIntOrNull() ?: -1
        if (contentLength > 0) {
            val bytes = ByteArray(contentLength)
            var totalRead = 0
            while (totalRead < contentLength) {
                val read = session.inputStream.read(bytes, totalRead, contentLength - totalRead)
                if (read == -1) break
                totalRead += read
            }
            String(bytes, 0, totalRead, Charsets.UTF_8)
        } else {
            val body = mutableMapOf<String, String>()
            session.parseBody(body)
            body["postData"] ?: ""
        }
    }

    private fun json(status: Response.Status, body: String): Response =
        NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", body).apply {
            addHeader("Access-Control-Allow-Origin", "*"); addHeader("Cache-Control", "no-store")
        }

    private fun d(msg: String) { if (BuildConfig.DEBUG) Log.d("IdeBridgeServer", msg) }
}