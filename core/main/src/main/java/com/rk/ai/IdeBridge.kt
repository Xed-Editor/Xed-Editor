package com.rk.ai

import android.os.Process
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.ai.bridge.server.IdeBridgeServer
import com.rk.ai.service.IdeServiceImpl
import com.rk.utils.getTempDir
import com.rk.xededitor.BuildConfig
import java.io.File
import java.security.SecureRandom

object IdeBridge {
    data class Info(val port: Int, val token: String)

    private var server: IdeBridgeServer? = null

    fun connectedClients(): Int = server?.connectedClients ?: 0
    fun availableTools(): Int = 28
    private var token: String? = null
    private var port: Int = 0
    private val secureRandom = SecureRandom()
    private val workspacePaths = mutableListOf<String>()
    private val workspacePathsLock = Any()

    fun isRunning(): Boolean = server != null

    fun getBridgeInfo(): Info? {
        val s = server ?: return null
        val t = token ?: return null
        return Info(s.port, t)
    }

    fun ensureStarted(viewModel: MainViewModel, workspacePath: String? = null): Info? {
        workspacePath?.let { setWorkspacePath(it) }
        if (server != null) return getBridgeInfo()

        runCatching {
            val t = newToken()
            token = t
            // Use 0 to let the OS pick an available port
            val s = IdeBridgeServer(0, t, IdeServiceImpl(viewModel))
            s.start()
            server = s
            port = s.port
            s.ideService = IdeServiceImpl(viewModel, s)

            synchronized(workspacePathsLock) {
                if (workspacePaths.isNotEmpty()) {
                    writeDiscoveryFile(port, t, workspacePathForResolution())
                }
            }
            if (BuildConfig.DEBUG) Log.d("IdeBridge", "Server started on port $port")
        }.onFailure {
            Log.e("IdeBridge", "Failed to start server", it)
            server = null
            token = null
            port = 0
        }
        
        return getBridgeInfo()
    }

    fun setWorkspacePath(path: String) {
        synchronized(workspacePathsLock) {
            if (!workspacePaths.contains(path)) {
                workspacePaths.add(path)
                val s = server
                val t = token
                if (s != null && t != null) {
                    writeDiscoveryFile(s.port, t, workspacePathForResolution())
                }
            }
        }
    }

    fun stop() {
        server?.stop()
        server = null
        token = null
        port = 0
        synchronized(workspacePathsLock) { workspacePaths.clear() }
        clearDiscoveryFilesForProcess()
    }

    /** Gets the most recently added workspace path as the primary one. */
    fun primaryWorkspacePath(): String = synchronized(workspacePathsLock) { workspacePaths.lastOrNull().orEmpty() }
    
    /** Gets all added workspace paths joined by the system path separator. */
    fun workspacePathForResolution(): String = synchronized(workspacePathsLock) { workspacePaths.joinToString(File.pathSeparator) }

    private fun newToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun writeDiscoveryFile(port: Int, token: String, workspacePath: String) {
        runCatching {
            val pid = Process.myPid()
            val config = JsonObject().apply {
                addProperty("url", "http://127.0.0.1:$port")
                addProperty("port", port)
                addProperty("token", token)
                addProperty("authToken", token)
                addProperty("workspacePath", workspacePath)
                addProperty("pid", pid)
                add("ideInfo", JsonObject().apply {
                    addProperty("name", "vscode")
                    addProperty("displayName", "Xed Editor")
                })
            }
            val json = GsonBuilder().setPrettyPrinting().create().toJson(config)

            listOf(
                File(getTempDir(), "gemini/ide"),
                File(getTempDir(), "terminal/gemini-sheet/gemini/ide"),
                File(getTempDir(), "ide-bridge"),
            ).forEach { dir ->
                dir.mkdirs()
                val prefix = if (dir.name == "ide-bridge") "ide-server-" else "gemini-ide-server-"
                dir.listFiles { file -> file.name.startsWith(prefix) && file.name.endsWith(".json") }
                    ?.forEach { file ->
                        val fileName = file.name
                        val parts = fileName.removePrefix(prefix).removeSuffix(".json").split("-")
                        val filePid = parts.firstOrNull()?.toIntOrNull()
                        if (filePid != null) {
                            if (filePid == pid) {
                                val filePort = parts.getOrNull(1)?.toIntOrNull()
                                if (filePort != null && filePort != port) {
                                    file.delete()
                                }
                            } else {
                                if (!isPidAlive(filePid)) {
                                    file.delete()
                                }
                            }
                        }
                    }

                val fileName = if (dir.name == "ide-bridge") "ide-server-$pid-$port.json" else "gemini-ide-server-$pid-$port.json"
                File(dir, fileName).writeText(json)
            }
        }
    }

    private fun isPidAlive(pid: Int): Boolean {
        return runCatching {
            File("/proc/$pid").exists()
        }.getOrDefault(true)
    }

    private fun clearDiscoveryFilesForProcess() {
        runCatching {
            val pid = Process.myPid()
            listOf(
                File(getTempDir(), "gemini/ide"),
                File(getTempDir(), "terminal/gemini-sheet/gemini/ide"),
                File(getTempDir(), "ide-bridge"),
            ).forEach { dir ->
                dir.listFiles { file -> file.name.contains("-$pid-") && file.name.endsWith(".json") }
                    ?.forEach { it.delete() }
            }
        }
    }
}
