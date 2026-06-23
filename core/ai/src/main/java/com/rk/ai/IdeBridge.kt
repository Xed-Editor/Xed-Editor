package com.rk.ai

import android.util.Log
import com.rk.activities.main.MainViewModel
import com.rk.ai.bridge.DiscoveryFileWriter
import com.rk.ai.bridge.McpToolRegistry
import com.rk.ai.bridge.server.McpSdkServer
import com.rk.ai.bridge.server.registerBuiltInTools
import com.rk.ai.bridge.stitch.ExternalMcpTool
import com.rk.ai.bridge.stitch.McpStitcher
import com.rk.ai.service.IdeServiceImpl
import com.rk.xededitor.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

private const val HEALTH_CHECK_TIMEOUT_MS = 2000

object IdeBridge {
    data class Info(val port: Int, val token: String, val host: String = "127.0.0.1")

    private var sdkServer: McpSdkServer? = null
    private val stitcher = McpStitcher()

    fun connectedClients(): Int = 0
    fun availableTools(): Int = synchronized(stateLock) { sdkServer?.toolRegistry?.listNames()?.size ?: 0 }
    private var token: String? = null
    private var port: Int = 0
    private var host: String = "127.0.0.1"
    private val secureRandom = SecureRandom()
    private val workspacePaths = mutableListOf<String>()
    private val workspacePathsLock = Any()
    private val stateLock = Any()

    val bridgeStitcher: McpStitcher get() = stitcher

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

            initStitcher()

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

    private fun initStitcher() {
        stitcher.onConfigChanged = { configJson ->
            onMcpServersConfigChanged?.invoke(configJson)
        }
        stitcher.setOnToolsChanged { externalTools ->
            val registry = synchronized(stateLock) { sdkServer?.toolRegistry } ?: return@setOnToolsChanged
            synchronized(registry) {
                val activeNames = HashSet<String>()
                for (tool in externalTools) { activeNames.add(tool.getName()) }
                for (n in registry.listNames()) {
                    if (n.startsWith("stitch_") && n !in activeNames) {
                        registry.remove(n)
                    }
                }
                for (tool in externalTools) {
                    registry.register(tool)
                }
            }
            synchronized(stateLock) { sdkServer?.rebuild() }
            if (BuildConfig.DEBUG) {
                Log.d("IdeBridge", "Stitcher: ${externalTools.size} external tools registered")
            }
        }
        val ws = workspacePaths.lastOrNull()
        if (ws != null) {
            stitcher.connectFromConfigFile(ws)
        }
        stitcher.connectAllFromSettings()
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
                stitcher.connectFromConfigFile(path)
            }
        }
    }

    fun stop() {
        synchronized(stateLock) {
            if (BuildConfig.DEBUG) Log.d("IdeBridge", "Stopping server")
            sdkServer?.stop()
            stitcher.disconnectAll()
            sdkServer = null
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

    fun refreshStitcher() {
        stitcher.refresh()
        synchronized(stateLock) { sdkServer?.rebuild() }
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
