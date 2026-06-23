package com.rk.ai.bridge.server

import android.util.Log
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.service.IdeService
import com.rk.xededitor.BuildConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

class McpSdkServer(
    private val host: String = "127.0.0.1",
    private val token: String,
    private val ideServiceProvider: () -> IdeService,
) {
    companion object {
        private const val TAG = "McpSdkServer"
        private const val MCP_PROTOCOL_VERSION = "2025-03-26"
    }

    @Volatile
    var toolRegistry: McpToolRegistry = McpToolRegistry()

    private var ktorServer: ApplicationEngine? = null

    val port: Int get() = ktorServer?.environment?.connectors?.firstOrNull()?.port ?: -1
    val isRunning: Boolean get() = ktorServer != null

    fun start(requestedPort: Int = 0, registry: McpToolRegistry): Int {
        toolRegistry = registry
        ktorServer = embeddedServer(CIO, host = host, port = requestedPort) {
            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Delete)
                allowNonSimpleContentTypes = true
                allowHeader("Authorization")
                allowHeader("Content-Type")
                allowHeader("Accept")
                allowHeader("Mcp-Session-Id")
                allowHeader("Mcp-Protocol-Version")
                exposeHeader("Mcp-Session-Id")
                exposeHeader("Mcp-Protocol-Version")
            }
            routing {
                route("/mcp") {
                    intercept(ApplicationCallPipeline.Call) {
                        if (!isAuthorized(call)) {
                            call.respondText(
                                "{\"error\":\"unauthorized\"}",
                                ContentType.Application.Json,
                                HttpStatusCode.Unauthorized,
                            )
                            finish()
                        }
                    }
                    mcpStreamableHttp {
                        buildSdkServer()
                    }
                }
                get("/health") {
                    call.respondText("{\"ok\":true}", ContentType.Application.Json)
                }
                get("/mcp-info") {
                    val info = buildMcpInfoJson()
                    call.respondText(info, ContentType.Application.Json)
                }
            }
        }.start(wait = false)
        val actualPort = port
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "MCP SDK server started on $host:$actualPort")
        }
        return actualPort
    }

    fun rebuild() {
        val p = port
        if (p <= 0) return
        ktorServer?.stop(1000, 3000)
        ktorServer = null
        start(p, toolRegistry)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "MCP SDK server rebuilt on $host:${ktorServer?.port()}")
        }
    }

    fun stop() {
        ktorServer?.stop(1000, 5000)
        ktorServer = null
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "MCP SDK server stopped")
        }
    }

    private fun buildSdkServer(): Server {
        val sdkServer = Server(
            serverInfo = Implementation(
                name = "xed-ide-bridge",
                version = "2.2.0",
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                ),
            ),
        )
        registerToolsToSdkServer(
            sdkServer = sdkServer,
            registry = toolRegistry,
            ideServiceProvider = ideServiceProvider,
            progressCallback = { name, msg ->
                Log.d(TAG, "Tool progress: $name - $msg")
            },
        )
        return sdkServer
    }

    private fun buildMcpInfoJson(): String = buildString {
        append("{\"port\":")
        append(port)
        append(",\"tools\":")
        append(toolRegistry.listNames().size)
        append(",\"tokenPrefix\":\"")
        append(token.take(8))
        append("\"}")
    }

    private fun isAuthorized(call: ApplicationCall): Boolean {
        val queryToken = call.request.queryParameters["token"]
        val auth = call.request.headers["Authorization"]?.trim().orEmpty()
        val headerToken = auth.split(" ", limit = 2)
            .takeIf { it.size == 2 && it[0].equals("Bearer", ignoreCase = true) }
            ?.get(1)
        return queryToken == token || headerToken == token
    }
}
