package com.rk.ai

import android.os.Process
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.ai.bridge.server.GeminiBridgeServer
import com.rk.ai.service.GeminiIdeServiceImpl
import com.rk.utils.getTempDir
import com.rk.xededitor.BuildConfig
import java.io.File
import java.security.SecureRandom

/**
 * Coordinator for the Gemini AI bridge.
 * Handles server lifecycle, discovery file management, and workspace tracking.
 */
object GeminiBridge {
    data class Info(val url: String, val port: Int, val token: String, val workspacePath: String)

    private var server: GeminiBridgeServer? = null
    var token: String? = null
        private set
    var port: Int = 0
        private set
        
    private val secureRandom = SecureRandom()
    private val workspacePaths = linkedSetOf<String>()
    private val workspacePathsLock = Any()
    
    private const val TAG = "GeminiBridge"
    private fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    @Synchronized
    fun ensureStarted(viewModel: MainViewModel, workspacePath: String): Info {
        d("ensureStarted(workspacePath=$workspacePath)")
        
        synchronized(workspacePathsLock) {
            if (workspacePath.isNotBlank()) workspacePaths.add(workspacePath)
        }

        val currentServer = server
        if (currentServer != null && currentServer.wasStarted()) {
            writeDiscoveryFile(currentServer.listeningPort, token!!, primaryWorkspacePath())
            return Info("http://127.0.0.1:${currentServer.listeningPort}", currentServer.listeningPort, token!!, primaryWorkspacePath())
        }

        val newToken = newToken()
        token = newToken
        
        // Initial service without notification sender
        val service = GeminiIdeServiceImpl(viewModel)
        service.ensureIdeEnabled()
        
        val newServer = GeminiBridgeServer(0, newToken, service)
        
        // Circular dependency injection: provide server (NotificationSender) to service
        newServer.ideService = GeminiIdeServiceImpl(viewModel, newServer)
        
        newServer.start()
        server = newServer
        port = newServer.listeningPort
        
        writeDiscoveryFile(port, newToken, primaryWorkspacePath())
        d("created bridge port=$port")
        
        return Info("http://127.0.0.1:$port", port, newToken, primaryWorkspacePath())
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        token = null
        port = 0
    }

    /** Gets the first added workspace path as the primary one. */
    fun primaryWorkspacePath(): String = synchronized(workspacePathsLock) { workspacePaths.firstOrNull().orEmpty() }
    
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
                addProperty("workspacePath", workspacePath)
                addProperty("pid", pid)
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
                dir.listFiles { file -> file.name.startsWith("gemini-ide-server-$pid-") && file.name.endsWith(".json") }
                    ?.filter { it.name != "gemini-ide-server-$pid-$port.json" }
                    ?.forEach { it.delete() }

                File(dir, "gemini-ide-server-$pid-$port.json").writeText(json)
            }
        }
    }
}
