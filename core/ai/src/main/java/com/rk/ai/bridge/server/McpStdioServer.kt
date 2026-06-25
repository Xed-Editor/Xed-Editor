package com.rk.ai.bridge.server

import android.util.Log
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.service.IdeService
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream

class McpStdioServer(
    private val ideServiceProvider: () -> IdeService,
) {
    companion object {
        private const val TAG = "McpStdioServer"
    }

    @Volatile
    var toolRegistry: McpToolRegistry = McpToolRegistry()

    private var serverScope: CoroutineScope? = null
    private var activeServer: Server? = null

    val isRunning: Boolean get() = activeServer != null

    fun start(
        registry: McpToolRegistry,
        workspacePaths: List<String>,
        input: java.io.InputStream = System.`in`,
        output: java.io.OutputStream = System.out,
    ) {
        if (isRunning) {
            if (com.rk.xededitor.BuildConfig.DEBUG) {
                Log.w(TAG, "Stdio server already running")
            }
            return
        }
        toolRegistry = registry
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        serverScope = scope

        val sdkServer = Server(
            serverInfo = Implementation(
                name = "xed-ide-bridge-stdio",
                version = "2.2.0",
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                    resources = ServerCapabilities.Resources(
                        listChanged = true,
                        subscribe = true,
                    ),
                    prompts = ServerCapabilities.Prompts(listChanged = true),
                ),
            ),
        )

        registerToolsToSdkServer(
            sdkServer = sdkServer,
            registry = registry,
            ideServiceProvider = ideServiceProvider,
            progressCallback = { name, msg ->
                if (com.rk.xededitor.BuildConfig.DEBUG) {
                    Log.d(TAG, "Tool progress: $name - $msg")
                }
            },
        )
        McpResourceProvider.registerResources(sdkServer, workspacePaths, ideServiceProvider)
        McpPromptProvider.registerPrompts(sdkServer)

        activeServer = sdkServer

        kotlinx.coroutines.runBlocking {
            try {
                val transport = StdioServerTransport(
                    inputStream = input.asSource().buffered(),
                    outputStream = output.asSink().buffered(),
                )
                sdkServer.createSession(transport)
                if (com.rk.xededitor.BuildConfig.DEBUG) {
                    Log.d(TAG, "Stdio MCP server started")
                }
            } catch (e: Exception) {
                if (com.rk.xededitor.BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to start stdio server", e)
                }
                activeServer = null
                scope.cancel()
            }
        }
    }

    fun startWithProcess(
        registry: McpToolRegistry,
        workspacePaths: List<String>,
        command: List<String>,
    ): Process? {
        if (isRunning) return null
        toolRegistry = registry

        return try {
            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(false)
            val process = pb.start()

            val sdkServer = createServer(registry, workspacePaths)
            activeServer = sdkServer

            val serverProcess = Thread(
                Runnable {
                    runBlocking {
                        val transport = StdioServerTransport(
                            inputStream = process.inputStream.asSource().buffered(),
                            outputStream = process.outputStream.asSink().buffered(),
                        )
                        sdkServer.createSession(transport)
                    }
                },
                "mcp-stdio-server",
            )
            serverProcess.isDaemon = true
            serverProcess.start()

            if (com.rk.xededitor.BuildConfig.DEBUG) {
                Log.d(TAG, "Started MCP stdio server with process: ${command.joinToString(" ")}")
            }
            process
        } catch (e: Exception) {
            if (com.rk.xededitor.BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to start stdio process", e)
            }
            null
        }
    }

    fun startWithPipes(
        registry: McpToolRegistry,
        workspacePaths: List<String>,
    ): Pair<PipedInputStream, PipedOutputStream> {
        val serverToClientIn = PipedInputStream(8192)
        val clientToServerOut = PipedOutputStream(serverToClientIn)

        val clientToServerIn = PipedInputStream(8192)
        val serverToClientOut = PipedOutputStream(clientToServerIn)

        val sdkServer = createServer(registry, workspacePaths)
        activeServer = sdkServer

        val thread = Thread(
            Runnable {
                runBlocking {
                    val transport = StdioServerTransport(
                        inputStream = clientToServerIn.asSource().buffered(),
                        outputStream = serverToClientOut.asSink().buffered(),
                    )
                    sdkServer.createSession(transport)
                }
            },
            "mcp-stdio-pipe-server",
        )
        thread.isDaemon = true
        thread.start()

        if (com.rk.xededitor.BuildConfig.DEBUG) {
            Log.d(TAG, "Started MCP stdio server with piped I/O")
        }
        return Pair(serverToClientIn, clientToServerOut)
    }

    private fun createServer(registry: McpToolRegistry, workspacePaths: List<String>): Server {
        val sdkServer = Server(
            serverInfo = Implementation(
                name = "xed-ide-bridge-stdio",
                version = "2.2.0",
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                    resources = ServerCapabilities.Resources(
                        listChanged = true,
                        subscribe = true,
                    ),
                    prompts = ServerCapabilities.Prompts(listChanged = true),
                ),
            ),
        )
        registerToolsToSdkServer(
            sdkServer = sdkServer,
            registry = registry,
            ideServiceProvider = ideServiceProvider,
            progressCallback = { name, msg ->
                if (com.rk.xededitor.BuildConfig.DEBUG) {
                    Log.d(TAG, "Tool progress: $name - $msg")
                }
            },
        )
        McpResourceProvider.registerResources(sdkServer, workspacePaths, ideServiceProvider)
        McpPromptProvider.registerPrompts(sdkServer)
        return sdkServer
    }

    fun stop() {
        runBlocking {
            activeServer?.close()
        }
        activeServer = null
        serverScope?.cancel()
        serverScope = null
        if (com.rk.xededitor.BuildConfig.DEBUG) {
            Log.d(TAG, "Stdio MCP server stopped")
        }
    }
}
