package com.rk.ai

import android.util.Log
import com.rk.activities.main.MainViewModel
import com.rk.ai.bridge.DiscoveryFileWriter
import com.rk.ai.bridge.server.IdeBridgeServer
import com.rk.ai.service.IdeServiceImpl
import com.rk.xededitor.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

private const val HEALTH_CHECK_TIMEOUT_MS = 2000

object IdeBridge {
    data class Info(val port: Int, val token: String, val host: String = "127.0.0.1")

    private var server: IdeBridgeServer? = null

    fun connectedClients(): Int = synchronized(stateLock) { server?.connectedClients ?: 0 }
    fun availableTools(): Int = synchronized(stateLock) { server?.toolsCount ?: 0 }
    private var token: String? = null
    private var port: Int = 0
    private var host: String = "127.0.0.1"
    private val secureRandom = SecureRandom()
    private val workspacePaths = mutableListOf<String>()
    private val workspacePathsLock = Any()
    private val stateLock = Any()

    fun isRunning(): Boolean = synchronized(stateLock) { server != null }

    fun getBridgeInfo(): Info? = synchronized(stateLock) {
        val s = server ?: return@synchronized null
        val t = token ?: return@synchronized null
        Info(s.port, t, host)
    }

    fun ensureStarted(viewModel: MainViewModel, workspacePath: String? = null): Info? {
        workspacePath?.let { setWorkspacePath(it) }
        synchronized(stateLock) {
            server?.let {
                it.stop()
                server = null
                token = null
                port = 0
            }
        }

        runCatching {
            val t = newToken()
            synchronized(stateLock) {
                token = t
                val s = IdeBridgeServer(0, t, IdeServiceImpl(viewModel))
                s.start()
                server = s
                port = s.port
                s.ideService = IdeServiceImpl(viewModel, s)
            }

            synchronized(workspacePathsLock) {
                if (workspacePaths.isNotEmpty()) {
                    writeDiscoveryFile(host, port, t, workspacePathForResolution())
                }
            }
            if (BuildConfig.DEBUG) {
                Log.d("IdeBridge", "Server started on $host:$port token=${t.take(8)}...")
                Log.d("IdeBridge", "Health: http://$host:$port/health")
            }
        }.onFailure {
            Log.e("IdeBridge", "Failed to start server", it)
            synchronized(stateLock) {
                server = null
                token = null
                port = 0
            }
        }
        
        return getBridgeInfo()
    }

    /** Check if the bridge is reachable via HTTP health check */
    fun healthCheck(): Boolean {
        val s = synchronized(stateLock) { server } ?: return false
        return runCatching {
            val url = URL("http://$host:${s.port}/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = HEALTH_CHECK_TIMEOUT_MS
            conn.readTimeout = HEALTH_CHECK_TIMEOUT_MS
            conn.responseCode == 200
        }.getOrDefault(false)
    }

    fun setWorkspacePath(path: String) {
        synchronized(workspacePathsLock) {
            if (!workspacePaths.contains(path)) {
                workspacePaths.add(path)
                val s = synchronized(stateLock) { server }
                val t = synchronized(stateLock) { token }
                if (s != null && t != null) {
                    writeDiscoveryFile(host, s.port, t, workspacePathForResolution())
                    server?.stitcher?.connectFromConfigFile(path)
                }
            }
        }
    }

    fun stop() {
        synchronized(stateLock) {
            if (BuildConfig.DEBUG) Log.d("IdeBridge", "Stopping server")
            server?.stop()
            server = null
            token = null
            port = 0
        }
        synchronized(workspacePathsLock) { workspacePaths.clear() }
        clearDiscoveryFilesForProcess()
    }

    fun primaryWorkspacePath(): String = synchronized(workspacePathsLock) { workspacePaths.lastOrNull().orEmpty() }
    
    fun workspacePathForResolution(): String = synchronized(workspacePathsLock) { workspacePaths.joinToString(File.pathSeparator) }

    fun hasWorkspacePath(): Boolean = synchronized(workspacePathsLock) { workspacePaths.isNotEmpty() }

    fun workspacePaths(): List<String> = synchronized(workspacePathsLock) { workspacePaths.toList() }

    fun refreshStitcherForWorkspace(path: String) {
        val s = synchronized(stateLock) { server }
        s?.stitcher?.connectFromConfigFile(path)
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
