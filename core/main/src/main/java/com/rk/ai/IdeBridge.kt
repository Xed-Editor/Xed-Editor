package com.rk.ai

import android.os.Process
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.ai.bridge.server.IdeBridgeServer
import com.rk.ai.service.IdeServiceImpl
import com.rk.file.child
import com.rk.utils.getTempDir
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
        runCatching {
            val pid = Process.myPid()
            val url = "http://$host:$port"
            val config = JsonObject().apply {
                addProperty("url", url)
                addProperty("host", host)
                addProperty("port", port)
                addProperty("token", token)
                addProperty("authToken", token)
                addProperty("workspacePath", workspacePath)
                addProperty("pid", pid)
                add("ideInfo", JsonObject().apply {
                    addProperty("name", "xed-ide")
                    addProperty("displayName", "Xed Editor")
                })
            }
            val json = GsonBuilder().setPrettyPrinting().create().toJson(config)

            // Write/merge OpenCode MCP config using standard mcpServers format
            runCatching {
                val opencodeConfigDir = com.rk.file.sandboxHomeDir().let { File(it, ".config/opencode") }
                opencodeConfigDir.mkdirs()
                val configFile = File(opencodeConfigDir, "opencode.json")
                val existingConfig = runCatching {
                    com.google.gson.JsonParser.parseString(configFile.readText()).asJsonObject
                }.getOrDefault(JsonObject())
                // Remove legacy 'mcp' key to avoid duplicate configs
                existingConfig.remove("mcp")
                val mcpServers = existingConfig.getAsJsonObject("mcpServers") ?: JsonObject().also {
                    existingConfig.add("mcpServers", it)
                }
                mcpServers.add("xed-ide", JsonObject().apply {
                    addProperty("type", "http")
                    addProperty("url", "$url/mcp")
                    addProperty("enabled", true)
                    add("headers", JsonObject().apply {
                        addProperty("Authorization", "Bearer $token")
                    })
                })
                configFile.writeText(GsonBuilder().setPrettyPrinting().create().toJson(existingConfig))
            }


            val tmpDir = getTempDir()
            val agentSheetDirs = listOf(
                "gemini",
                "opencode",
            )

            val dirs = mutableListOf<File>().apply {
                add(File(tmpDir, "gemini/ide"))
                add(File(tmpDir, "terminal/gemini-sheet/gemini/ide"))
                agentSheetDirs.forEach { agent ->
                    add(File(tmpDir, "terminal/$agent-sheet/gemini/ide"))
                    add(File(tmpDir, "terminal/$agent-sheet/$agent/ide"))
                }
                add(File(tmpDir, "ide-bridge"))
                add(File("/tmp", "xed-ide"))
                workspacePath.split(File.pathSeparator).forEach { wp ->
                    if (wp.isNotBlank()) {
                        add(File(wp, ".xed"))
                        add(File(wp, ".opencode"))
                    }
                }
            }

            dirs.forEach { dir ->
                runCatching {
                    dir.mkdirs()
                    val prefix = if (dir.name == "ide-bridge") "ide-server-" else "gemini-ide-server-"
                    dir.listFiles { file -> file.name.startsWith(prefix) && file.name.endsWith(".json") }
                        ?.forEach { file ->
                            val parts = file.name.removePrefix(prefix).removeSuffix(".json").split("-")
                            val filePid = parts.firstOrNull()?.toIntOrNull()
                            if (filePid != null) {
                                if (filePid == pid) {
                                    val filePort = parts.getOrNull(1)?.toIntOrNull()
                                    if (filePort != null && filePort != port) file.delete()
                                } else {
                                    if (!isPidAlive(filePid)) file.delete()
                                }
                            }
                        }
                    val fileName = if (dir.name == "ide-bridge") "ide-server-$pid-$port.json" else "gemini-ide-server-$pid-$port.json"
                    File(dir, fileName).writeText(json)
                    if (dir.name == ".xed") {
                        File(dir, "ide.json").writeText(json)
                        File(dir, "ide.env").writeText(
                            buildString {
                                appendLine("export XED_IDE_URL=$url")
                                appendLine("export XED_IDE_HOST=$host")
                                appendLine("export XED_IDE_PORT=$port")
                                appendLine("export XED_IDE_AUTH_TOKEN=$token")
                                appendLine("export IDE_SERVER_PORT=$port")
                                appendLine("export IDE_AUTH_TOKEN=$token")
                            }
                        )
                    }
                    if (dir.name == ".opencode") {
                        val existingMcp = runCatching {
                            com.google.gson.JsonParser.parseString(File(dir, "mcp.json").readText()).asJsonObject
                        }.getOrDefault(JsonObject())
                        // Remove legacy 'mcp' key to avoid duplicate configs
                        existingMcp.remove("mcp")
                        val mcpServers = existingMcp.getAsJsonObject("mcpServers") ?: JsonObject().also {
                            existingMcp.add("mcpServers", it)
                        }
                        mcpServers.add("xed-ide", JsonObject().apply {
                            addProperty("type", "http")
                            addProperty("url", "$url/mcp")
                            addProperty("enabled", true)
                            add("headers", JsonObject().apply {
                                addProperty("Authorization", "Bearer $token")
                            })
                        })
                        File(dir, "mcp.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(existingMcp))
                    }
                }
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
            val tmpDir = getTempDir()
            val dirs = listOf(
                File(tmpDir, "gemini/ide"),
                File(tmpDir, "terminal/gemini-sheet/gemini/ide"),
                File(tmpDir, "terminal/opencode-sheet/gemini/ide"),
                File(tmpDir, "terminal/opencode-sheet/opencode/ide"),
                File(tmpDir, "ide-bridge"),
                File("/tmp", "xed-ide"),
            )
            dirs.forEach { dir ->
                dir.listFiles { file -> file.name.contains("-$pid-") && file.name.endsWith(".json") }
                    ?.forEach { it.delete() }
            }
        }
    }
}
