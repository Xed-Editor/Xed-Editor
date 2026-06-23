package com.rk.ai

import android.util.Log
import com.rk.activities.main.MainViewModel
import com.rk.ai.bridge.DiscoveryFileWriter
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.bridge.server.McpSdkServer
import com.rk.ai.bridge.server.McpStdioServer
import com.rk.ai.bridge.server.registerBuiltInTools
import com.rk.ai.bridge.external.ExternalMcpManager
import com.rk.ai.bridge.external.ExternalMcpTool
import com.rk.ai.service.IdeServiceImpl
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

private const val HEALTH_CHECK_TIMEOUT_MS = 2000

object IdeBridge {
    data class Info(val port: Int, val token: String, val host: String = "127.0.0.1")

    private var sdkServer: McpSdkServer? = null
    private var stdioServer: McpStdioServer? = null
    private val externalManager = ExternalMcpManager()

    fun connectedClients(): Int = externalManager.connectedServers.size
    fun availableTools(): Int = synchronized(stateLock) { sdkServer?.toolRegistry?.listNames()?.size ?: 0 }
    private var token: String? = null
    private var port: Int = 0
    private var host: String = "127.0.0.1"
    private val secureRandom = SecureRandom()
    private val workspacePaths = mutableListOf<String>()
    private val workspacePathsLock = Any()
    private val stateLock = Any()

    val bridgeExternalManager: ExternalMcpManager get() = externalManager

    var onMcpServersConfigChanged: ((configJson: String) -> Unit)? = null

    fun isRunning(): Boolean = synchronized(stateLock) { sdkServer?.isRunning == true }

    fun getBridgeInfo(): Info? = synchronized(stateLock) {
        val s = sdkServer ?: return@synchronized null
        val t = token ?: return@synchronized null
        if (!s.isRunning) return@synchronized null
        Info(s.port, t, host)
    }

    fun ensureStarted(viewModel: MainViewModel, workspacePath: String? = null): Info? {
        synchronized(stateLock) {
            sdkServer?.let { it.stop(); sdkServer = null }
            token = null
            port = 0
        }

        runCatching {
            val t = newToken()
            synchronized(stateLock) {
                token = t
                val s = McpSdkServer(
                    host = host,
                    token = t,
                    ideServiceProvider = { IdeServiceImpl(viewModel) },
                )
                s.start(requestedPort = 0, registry = buildToolRegistry())
                sdkServer = s
                port = s.port
            }

            initExternalManager()

            if (workspacePath != null) {
                addWorkspacePath(workspacePath)
            }

            if (BuildConfig.DEBUG) {
                Log.d("IdeBridge", "MCP SDK server started on $host:$port token=${t.take(8)}...")
            }
        }.onFailure {
            Log.e("IdeBridge", "Failed to start server", it)
            synchronized(stateLock) { sdkServer = null; token = null; port = 0 }
        }

        return getBridgeInfo()
    }

    private fun buildToolRegistry(): McpToolRegistry {
        val registry = McpToolRegistry()
        registerBuiltInTools(registry)
        return registry
    }

    private fun initExternalManager() {
        externalManager.onConfigChanged = { configJson ->
            onMcpServersConfigChanged?.invoke(configJson)
        }
        externalManager.setOnToolsChanged { externalTools ->
            val registry = synchronized(stateLock) { sdkServer?.toolRegistry } ?: return@setOnToolsChanged
            synchronized(registry) {
                val activeNames = HashSet<String>()
                for (tool in externalTools) { activeNames.add(tool.getName()) }
                for (n in registry.listNames()) {
                    if (n.startsWith("ext_") && n !in activeNames) {
                        registry.remove(n)
                    }
                }
                for (tool in externalTools) {
                    registry.register(tool)
                }
            }
            synchronized(stateLock) { sdkServer?.rebuild() }
            if (BuildConfig.DEBUG) {
                Log.d("IdeBridge", "External MCP: ${externalTools.size} external tools registered")
            }
        }
        val ws = workspacePaths.lastOrNull()
        if (ws != null) {
            externalManager.connectFromConfigFile(ws)
        }
        externalManager.connectAllFromSettings()
    }

    fun healthCheck(): Boolean {
        val s = synchronized(stateLock) { sdkServer } ?: return false
        return s.isRunning && runCatching {
            val url = URL("http://$host:${s.port}/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = HEALTH_CHECK_TIMEOUT_MS
            conn.readTimeout = HEALTH_CHECK_TIMEOUT_MS
            conn.responseCode == 200
        }.getOrDefault(false)
    }

    fun setWorkspacePath(path: String) {
        addWorkspacePath(path)
    }

    private fun addWorkspacePath(path: String) {
        synchronized(workspacePathsLock) {
            if (!workspacePaths.contains(path)) {
                workspacePaths.add(path)
                val sdk = synchronized(stateLock) { sdkServer }
                val t = synchronized(stateLock) { token }
                if (sdk != null && t != null) {
                    writeDiscoveryFile(host, sdk.port, t, workspacePathForResolution())
                }
                externalManager.connectFromConfigFile(path)
            }
        }
    }

    fun startStdioServer(): Boolean {
        val registry = buildToolRegistry()
        val server = McpStdioServer(
            ideServiceProvider = {
                val vm = com.rk.activities.main.MainViewModel::class.java
                    .getDeclaredConstructor().newInstance()
                IdeServiceImpl(vm)
            },
        )
        server.start(
            registry = registry,
            workspacePaths = workspacePaths(),
        )
        stdioServer = server
        if (BuildConfig.DEBUG) {
            Log.d("IdeBridge", "Stdio MCP server started")
        }
        return true
    }

    fun startStdioWithProcess(command: List<String>): Process? {
        val registry = buildToolRegistry()
        val server = McpStdioServer(
            ideServiceProvider = {
                val vm = com.rk.activities.main.MainViewModel::class.java
                    .getDeclaredConstructor().newInstance()
                IdeServiceImpl(vm)
            },
        )
        val process = server.startWithProcess(
            registry = registry,
            workspacePaths = workspacePaths(),
            command = command,
        )
        if (process != null) {
            stdioServer = server
            if (BuildConfig.DEBUG) {
                Log.d("IdeBridge", "Stdio MCP server started with process")
            }
        }
        return process
    }

    fun stopStdioServer() {
        stdioServer?.stop()
        stdioServer = null
    }

    fun stop() {
        synchronized(stateLock) {
            if (BuildConfig.DEBUG) Log.d("IdeBridge", "Stopping server")
            sdkServer?.stop()
            stdioServer?.stop()
            runBlocking { externalManager.disconnectAll() }
            sdkServer = null
            stdioServer = null
            token = null
            port = 0
        }
        synchronized(workspacePathsLock) { workspacePaths.clear() }
        clearDiscoveryFilesForProcess()
    }

    fun primaryWorkspacePath(): String = synchronized(workspacePathsLock) { workspacePaths.lastOrNull().orEmpty() }

    fun workspacePathForResolution(): String =
        synchronized(workspacePathsLock) { workspacePaths.joinToString(File.pathSeparator) }

    fun hasWorkspacePath(): Boolean = synchronized(workspacePathsLock) { workspacePaths.isNotEmpty() }

    fun workspacePaths(): List<String> = synchronized(workspacePathsLock) { workspacePaths.toList() }

    fun refreshExternalMcp() {
        externalManager.refresh()
        synchronized(stateLock) { sdkServer?.rebuild() }
    }

    fun getExternalMcpStatus(): Map<String, Any>? {
        if (!isRunning()) return null
        val statusList = externalManager.getClientStatus()
        val serverCount = statusList.size
        val toolCount = externalManager.toolSchemas.size
        return mapOf(
            "servers" to serverCount,
            "tools" to toolCount,
            "status" to statusList,
        )
    }

    private fun newToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun writeDiscoveryFile(host: String, port: Int, token: String, workspacePath: String) {
        DiscoveryFileWriter.write(DiscoveryFileWriter.BridgeInfo(host, port, token, workspacePath))
    }

    private fun clearDiscoveryFilesForProcess() {
        DiscoveryFileWriter.clearForProcess()
    }
}
