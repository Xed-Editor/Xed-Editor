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

object IdeBridge {
    data class Info(val port: Int, val token: String, val host: String = "127.0.0.1")

    private var server: IdeBridgeServer? = null

    fun connectedClients(): Int = server?.connectedClients ?: 0
    fun availableTools(): Int = IdeMcpTools.list().size()
    private var token: String? = null
    private var port: Int = 0
    private var host: String = "127.0.0.1"
    private val secureRandom = SecureRandom()
    private val workspacePaths = mutableListOf<String>()
    private val workspacePathsLock = Any()

    fun isRunning(): Boolean = server != null

    fun getBridgeInfo(): Info? {
        val s = server ?: return null
        val t = token ?: return null
        return Info(s.port, t, host)
    }

    fun ensureStarted(viewModel: MainViewModel, workspacePath: String? = null): Info? {
        workspacePath?.let { setWorkspacePath(it) }
        if (server != null) return getBridgeInfo()

        runCatching {
            val t = newToken()
            token = t
            val s = IdeBridgeServer(0, t, IdeServiceImpl(viewModel))
            s.start()
            server = s
            port = s.port
            s.ideService = IdeServiceImpl(viewModel, s)

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
            server = null
            token = null
            port = 0
        }
        
        return getBridgeInfo()
    }

    /** Check if the bridge is reachable via HTTP health check */
    fun healthCheck(): Boolean {
        val s = server ?: return false
        return runCatching {
            val url = URL("http://$host:${s.port}/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.responseCode == 200
        }.getOrDefault(false)
    }

    fun setWorkspacePath(path: String) {
        synchronized(workspacePathsLock) {
            if (!workspacePaths.contains(path)) {
                workspacePaths.add(path)
                val s = server
                val t = token
                if (s != null && t != null) {
                    writeDiscoveryFile(host, s.port, t, workspacePathForResolution())
                }
            }
        }
    }

    fun stop() {
        Log.d("IdeBridge", "Stopping server")
        server?.stop()
        server = null
        token = null
        port = 0
        synchronized(workspacePathsLock) { workspacePaths.clear() }
        clearDiscoveryFilesForProcess()
    }

    fun primaryWorkspacePath(): String = synchronized(workspacePathsLock) { workspacePaths.lastOrNull().orEmpty() }
    
    fun workspacePathForResolution(): String = synchronized(workspacePathsLock) { workspacePaths.joinToString(File.pathSeparator) }

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
