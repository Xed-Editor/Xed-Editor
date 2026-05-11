package com.rk.ai

import android.os.Process
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.ai.bridge.server.GeminiBridgeServer
import com.rk.ai.service.GeminiIdeServiceImpl
import com.rk.file.getTempDir
import com.rk.xededitor.BuildConfig
import java.io.File
import java.security.SecureRandom

object GeminiBridge {
    data class Info(val port: Int, val token: String)

    private var server: GeminiBridgeServer? = null
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

    fun startServer(viewModel: MainViewModel) {
        if (server != null) return

        runCatching {
            val t = newToken()
            token = t
            // Use 0 to let the OS pick an available port
            val s = GeminiBridgeServer(0, t, GeminiIdeServiceImpl(viewModel))
            s.start()
            server = s
            port = s.port
            s.ideService = GeminiIdeServiceImpl(viewModel, s)

            synchronized(workspacePathsLock) {
                if (workspacePaths.isNotEmpty()) {
                    writeDiscoveryFile(port, t, workspacePathForResolution())
                }
            }
            if (BuildConfig.DEBUG) Log.d("GeminiBridge", "Server started on port $port")
        }.onFailure {
            Log.e("GeminiBridge", "Failed to start server", it)
            server = null
            token = null
            port = 0
        }
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

    fun stopServer() {
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

            // Gemini CLI checks discovery files under "$TMPDIR/gemini/ide".
            // The embedded sheet overrides TMPDIR to terminal/gemini-sheet, while
            // normal terminal sessions use the app temp directory directly.
            listOf(
                File(getTempDir(), "gemini/ide"),
                File(getTempDir(), "terminal/gemini-sheet/gemini/ide"),
            ).forEach { dir ->
                dir.mkdirs()
                dir.listFiles { file -> file.name.startsWith("gemini-ide-server-") && file.name.endsWith(".json") }
                    ?.forEach { file ->
                        val fileName = file.name
                        // Pattern: gemini-ide-server-$PID-$PORT.json or gemini-ide-server-$PID.json
                        val parts = fileName.removePrefix("gemini-ide-server-").removeSuffix(".json").split("-")
                        val filePid = parts.firstOrNull()?.toIntOrNull()
                        if (filePid != null) {
                            if (filePid == pid) {
                                // Delete old ports for our own PID
                                val filePort = parts.getOrNull(1)?.toIntOrNull()
                                if (filePort != null && filePort != port) {
                                    file.delete()
                                }
                            } else {
                                // Prune dead PIDs
                                if (!isPidAlive(filePid)) {
                                    file.delete()
                                }
                            }
                        }
                    }

                File(dir, "gemini-ide-server-$pid-$port.json").writeText(json)
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
            ).forEach { dir ->
                dir.listFiles { file -> file.name.startsWith("gemini-ide-server-$pid-") && file.name.endsWith(".json") }
                    ?.forEach { it.delete() }
            }
        }
    }
}
