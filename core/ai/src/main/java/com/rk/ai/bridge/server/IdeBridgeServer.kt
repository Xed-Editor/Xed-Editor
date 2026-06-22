package com.rk.ai.bridge.server

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.bridge.stitch.ExternalMcpTool
import com.rk.ai.bridge.tools.*
import com.rk.ai.service.IdeService
import com.rk.xededitor.BuildConfig
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.InetSocketAddress
import java.util.concurrent.TimeoutException

class IdeBridgeServer(
    preferredPort: Int = 0,
    private val token: String,
    private var ideService: IdeService,
) : NanoHTTPD(preferredPort) {
    companion object { private const val TAG = "IdeBridgeServer" }
    private val gson = GsonBuilder().create()
    private val toolRegistry = McpToolRegistry()
    private val sseSessionTracker = SseSessionTracker()
    private val httpSessionTracker = HttpSessionTracker()
    private val jsonParser = JsonParser()
    val stitcher = McpStitcher()
    var connectedClients: Int = 0; private set
    val toolsCount: Int get() = toolRegistry.listSchemas().size()

    init {
        initStitcher()
        registerTools(toolRegistry)
    }

    private fun initStitcher() {
        stitcher.setOnToolsChanged { tools ->
            val list = tools
            synchronized(this) {
                val activeNames = java.util.HashSet<String>()
                for (t in list) { activeNames.add(t.getName()) }
                val names = toolRegistry.listNames()
                for (n in names) {
                    if (n.startsWith("stitch_") && n !in activeNames) {
                        toolRegistry.remove(n)
                    }
                }
                for (t in list) {
                    toolRegistry.register(t)
                }
            }
            if (BuildConfig.DEBUG) {
                Log.d("IdeBridgeServer", "Stitcher: ${list.size} external tools registered")
            }
        }
        val ws = runCatching { ideService.getPrimaryWorkspacePath() }.getOrNull()
        if (ws != null) {
            stitcher.connectFromConfigFile(ws)
        }
        stitcher.connectAllFromSettings()
    }

    fun refreshStitcher() {
        stitcher.refresh()
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
            register(WebFetchTool()); register(WebSearchTool()); register(WebDownloadTool()); register(WebResearchTool())
            register(GitHubRepoInfoTool()); register(GitHubReadmeTool())
            register(GitHubSearchCodeTool()); register(GitHubFileFetchTool())
            register(NpmSearchTool()); register(PipSearchTool()); register(MavenSearchTool())
            register(CodeReviewTool()); register(TestGeneratorTool())
            register(ContextManagerTool()); register(TaskPlannerTool())
            register(DocGeneratorTool()); register(CodebaseIndexerTool())
            register(SemanticSearchTool())
            register(CustomInstructionsTool()); register(AgentWorkflowTool())
            register(PlanModeTool()); register(DependencyAnalyzerTool())
            register(McpStitcherConfigTool())
        }
    }

    override fun serve(session: IHTTPSession): Response {
        d("serve ${session.method} ${session.uri}")
        if (session.method == Method.OPTIONS) return options()
        val rawPostBody = if (session.method == Method.POST) {
            val len = Math.min(session.bodySize, MAX_BODY_SIZE)
            val buf = ByteArray(len)
            session.inputStream.readFully(buf)
            String(buf)
        } else ""

        if (session.uri != "/health" && session.uri != "/debug" && !isAuthorized(session))
            return json(Response.Status.UNAUTHORIZED, errorJson(null, -32001, "unauthorized"))

        return when (session.uri) {
            "/health" -> json(Response.Status.OK, """{"status":"ok"}""")
            "/debug" -> json(Response.Status.OK, debugJson())
            "/mcp-info" -> json(Response.Status.OK, bridgeInfoJson())
            "/stitch" -> json(Response.Status.OK, stitchInfoJson())
            "/stitch/refresh" -> { refreshStitcher(); json(Response.Status.OK, stitchInfoJson()) }
            else -> handleSseOrMcp(session, rawPostBody)
        }
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val query = session.queryParameterString
        val authHeader = session.headers["Authorization"] ?: session.headers["authorization"]
        if (query.contains("token=$token")) return true
        if (authHeader == "Bearer $token") return true
        if (authHeader == "Basic $token") return true
        return false
    }

    private fun handleSseOrMcp(session: IHTTPSession, rawPostBody: String): Response {
        val accept = session.headers["Accept"] ?: session.headers["accept"] ?: ""
        return if (accept.contains("text/event-stream")) {
            handleSse(session)
        } else if (rawPostBody.isNotBlank()) {
            handleMcp(session, rawPostBody)
        } else {
            json(Response.Status.BAD_REQUEST, errorJson(null, -32600, "Not an MCP or SSE request"))
        }
    }

    private fun handleSse(session: IHTTPSession): Response {
        connectedClients++
        val encoder = ChunkedOutputStream()
        val sessionId = sseSessionTracker.register(session, encoder)
        val httpSessionId = httpSessionTracker.register(sessionId)
        sendEndpointEvent(encoder, "http://127.0.0.1:$port/mcp?token=$token&sessionId=$sessionId", sessionId)
        val chunks = encoder.toChunkedResponse()
        try { if (chunks != null) writeChunkedResponse(session, chunks) }
        catch (_: Exception) { }
        finally {
            sseSessionTracker.unregister(sessionId)
            httpSessionTracker.unregister(httpSessionId)
            connectedClients--
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "")
    }

    private fun sendEndpointEvent(encoder: ChunkedOutputStream, endpoint: String, sessionId: String) {
        encoder.add("event: endpoint\ndata: $endpoint?id=$sessionId\n\n")
    }

    private fun handleMcp(session: IHTTPSession, rawPostBody: String): Response {
        val jsonRequest = try { jsonParser.parse(rawPostBody).asJsonObject } catch (_: Exception) {
            return json(Response.Status.BAD_REQUEST, errorJson(null, -32700, "Parse error"))
        }
        val id = jsonRequest.get("id")
        val method = jsonRequest.get("method")?.asString ?: return json(Response.Status.OK, errorJson(id, -32600, "Method not specified"))
        return when (method) {
            "tools/list" -> json(Response.Status.OK, listTools(id))
            "tools/call" -> callTool(id, jsonRequest, session)
            "initialize" -> json(Response.Status.OK, initializeResponse(id))
            "notifications/initialized" -> json(Response.Status.OK, emptyJson(id))
            else -> json(Response.Status.OK, errorJson(id, -32601, "Unknown method: $method"))
        }
    }

    private fun callTool(id: JsonElement, jsonRequest: JsonObject, session: IHTTPSession): Response {
        val name = jsonRequest.getAsJsonObject("params")?.get("name")?.asString ?: return json(Response.Status.OK, errorJson(id, -32602, "Missing tool name"))
        val args = jsonRequest.getAsJsonObject("params")?.getAsJsonObject("arguments") ?: JsonObject()
        val sseSessionId = session.parameters.get("sessionId")?.firstOrNull()
        val context = BridgeToolContext(name, sseSessionId)
        val result = runBlocking {
            runCatching {
                val rawTimeoutMs = toolRegistry.getTimeoutMs(name)
                withTimeout(rawTimeoutMs) { toolRegistry.execute(name, args, context) }
            }.onFailure { e ->
                if (e is TimeoutException) return@runBlocking errorJson(id, -32001, "Request timed out")
                return@runBlocking errorJson(id, -32603, e.message ?: "Execution error")
            }.getOrNull()
        }
        val jsonResponse = if (result != null) result.toJson(id) else errorJson(id, -32602, "Unknown tool: $name")
        return json(Response.Status.OK, jsonResponse)
    }

    private fun listTools(id: JsonElement): String = gson.toJson(JsonObject().apply {
        add("jsonrpc", JsonNull.INSTANCE); add("id", id); add("result", JsonObject().apply {
            add("tools", toolRegistry.listSchemas())
        })
    })

    private fun initializeResponse(id: JsonElement): String = gson.toJson(JsonObject().apply {
        add("jsonrpc", JsonNull.INSTANCE); add("id", id)
        add("result", JsonObject().apply {
            add("protocolVersion", JsonNull.INSTANCE)
            add("capabilities", JsonObject())
            add("serverInfo", JsonObject().apply {
                addProperty("name", "xed-ide-bridge"); addProperty("version", "2.2.0")
            })
        })
    })

    private fun emptyJson(id: JsonElement): String = gson.toJson(JsonObject().apply {
        add("jsonrpc", JsonNull.INSTANCE); add("id", id); add("result", JsonObject())
    })

    private fun errorJson(id: JsonElement?, code: Int, msg: String): String = gson.toJson(JsonObject().apply {
        add("jsonrpc", JsonNull.INSTANCE); if (id != null) add("id", id) else add("id", JsonNull.INSTANCE)
        add("error", JsonObject().apply { addProperty("code", code); addProperty("message", msg) })
    })

    private fun bridgeInfoJson(): String = gson.toJson(JsonObject().apply {
        addProperty("name", "xed-ide-bridge"); addProperty("version", "2.2.0"); addProperty("protocol", "mcp")
        addProperty("tools", toolRegistry.listSchemas().size()); addProperty("clients", connectedClients)
        addProperty("sseClients", httpSessionTracker.sseSessionCount)
        addProperty("stitchServers", stitcher.connectedServers.size)
        addProperty("stitchTools", stitcher.toolSchemas.size)
    })

    private fun stitchInfoJson(): String = gson.toJson(JsonObject().apply {
        addProperty("servers", stitcher.connectedServers.size)
        addProperty("tools", stitcher.toolSchemas.size)
        add("status", com.google.gson.JsonArray().apply {
            stitcher.getClientStatus().forEach { status ->
                add(JsonObject().apply {
                    addProperty("name", status["name"]?.toString() ?: "")
                    addProperty("url", status["url"]?.toString() ?: "")
                    addProperty("reachable", status["reachable"] as? Boolean ?: false)
                    addProperty("toolCount", status["toolCount"] as? Int ?: 0)
                })
            }
        })
    })

    private fun debugJson(): String = gson.toJson(JsonObject().apply {
        addProperty("port", listeningPort); addProperty("clients", connectedClients)
        addProperty("sseClients", httpSessionTracker.sseSessionCount)
        addProperty("httpClients", httpSessionTracker.httpSessionCount)
        addProperty("tools", toolRegistry.listSchemas().size()); addProperty("tokenPrefix", token.take(8))
        addProperty("stitchServers", stitcher.connectedServers.size)
        addProperty("stitchTools", stitcher.toolSchemas.size)
    })

    override fun stop() {
        stitcher.disconnectAll()
        super.stop()
    }

    override fun start() { super.start(); d("Server listening on http://127.0.0.1:$port") }
    override fun start(timeout: Boolean) { super.start(timeout); d("Server started w/timeout=$timeout") }
    fun getListeningPort(): Int = port
    override fun makeSocket(host: String): java.net.ServerSocket =
        java.net.ServerSocket(InetSocketAddress(host, preferredPort), 50)

    private fun d(msg: String) { if (BuildConfig.DEBUG) Log.d(TAG, msg) }

    companion object {
        private const val MAX_BODY_SIZE = 2 * 1024 * 1024
        private const val SSE_SEND_TIMEOUT_MS = 30_000L
        private const val SSE_QUEUE_CAPACITY = 128
    }
}
