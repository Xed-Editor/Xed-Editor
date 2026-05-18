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
    data class Info(val port: Int, val token: String, val workspacePath: String, val host: String = "127.0.0.1")

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
    private var lastError: String? = null

    fun isRunning(): Boolean = synchronized(stateLock) { server != null }

    fun getBridgeInfo(): Info? = synchronized(stateLock) {
        val s = server ?: return@synchronized null
        val t = token ?: return@synchronized null
        Info(s.port, t, workspacePathForResolution(), host)
    }

    fun getLastError(): String? = synchronized(stateLock) { lastError }

    fun clearLastError() { synchronized(stateLock) { lastError = null } }

    fun ensureStarted(viewModel: MainViewModel, workspacePath: String? = null): Info? {
        workspacePath?.let { setWorkspacePath(it) }
        synchronized(stateLock) {
            if (server != null) {
                val health = healthCheck()
                if (!health) {
                    Log.w("IdeBridge", "Bridge unhealthy, restarting...")
                    server = null
                    token = null
                    port = 0
                } else {
                    val info = getBridgeInfo() ?: return@synchronized null
                    synchronized(workspacePathsLock) {
                        if (workspacePaths.isNotEmpty()) {
                            writeDiscoveryFile(host, info.port, info.token, workspacePathForResolution())
                        }
                    }
                    return info
                }
            }
        }

        lastError = null
        return runCatching {
            val t = newToken()
            val s = IdeBridgeServer(0, t, IdeServiceImpl(viewModel))
            s.start()
            val actualPort = s.listeningPort
            if (actualPort <= 0) throw RuntimeException("Failed to bind to any port")
            synchronized(stateLock) {
                server = s
                token = t
                port = actualPort
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
            getBridgeInfo()
        }.onFailure { e ->
            Log.e("IdeBridge", "Failed to start server", e)
            lastError = e.message
            synchronized(stateLock) {
                server = null
                token = null
                port = 0
            }
        }.getOrNull()
    }

    fun restart(viewModel: MainViewModel): Info? {
        stop()
        return ensureStarted(viewModel)
    }

    fun healthCheck(): Boolean = healthCheckInternal(2)

    private fun healthCheckInternal(retries: Int): Boolean {
        val s = synchronized(stateLock) { server } ?: return false
        repeat(retries) { attempt ->
            val result = runCatching {
                val url = URL("http://$host:${s.port}/health")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val response = conn.responseCode == 200
                conn.disconnect()
                response
            }.getOrDefault(false)
            if (result) return true
            if (attempt < retries - 1) {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.delay(500L * (attempt + 1))
                }
            }
        }
        return false
    }

    fun checkMcpConnection(): Pair<Boolean, String> {
        val info = getBridgeInfo() ?: return false to "Bridge not running"
        return try {
            val url = URL("http://${info.host}:${info.port}/mcp-info")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer ${info.token}")
            conn.setRequestProperty("x-ide-token", info.token)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val response = conn.responseCode
            if (response == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val hasTools = body.contains("\"tools\"")
                if (hasTools) {
                    val toolsCount = body.substringAfter("\"tools\":").substringBefore(",").trim()
                    Pair(true, "MCP connected: $toolsCount tools")
                } else {
                    Pair(true, "MCP connected")
                }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Pair(false, "MCP responded with code $response: $errorBody")
            }
        } catch (e: Exception) {
            Pair(false, "Connection failed: ${e.message}")
        }
    }

    fun verifyMcpToolsAvailable(): Pair<Boolean, String> {
        val info = getBridgeInfo() ?: return false to "Bridge not running"
        return try {
            val jsonRequest = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""".toByteArray()
            val url = URL("http://${info.host}:${info.port}/mcp")
            val conn = url.openConnection() as HttpURLConnection
            conn.doOutput = true
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${info.token}")
            conn.setRequestProperty("x-ide-token", info.token)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.outputStream.write(jsonRequest)
            conn.outputStream.flush()
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (body.contains("\"result\"") && body.contains("\"tools\"")) {
                    val toolsCount = body.split("\"name\":").size - 1
                    Pair(true, "$toolsCount MCP tools available")
                } else {
                    Pair(false, "MCP response missing tools: ${body.take(200)}")
                }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Pair(false, "MCP tools/list returned $responseCode: $errorBody")
            }
        } catch (e: Exception) {
            Pair(false, "MCP tools/list failed: ${e.message}")
        }
    }

    fun setWorkspacePath(path: String) {
        synchronized(workspacePathsLock) {
            if (!workspacePaths.contains(path)) {
                workspacePaths.add(path)
            }
            val s = synchronized(stateLock) { server }
            val t = synchronized(stateLock) { token }
            if (s != null && t != null) {
                writeDiscoveryFile(host, s.port, t, workspacePathForResolution())
            }
        }
    }

    fun stop() {
        synchronized(stateLock) {
            Log.d("IdeBridge", "Stopping server")
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

    private fun newToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun writeDiscoveryFile(host: String, port: Int, token: String, workspacePath: String) {
        val info = DiscoveryFileWriter.BridgeInfo(host, port, token, workspacePath)
        DiscoveryFileWriter.write(info)
        DiscoveryFileWriter.forceWriteAgentConfigs(info)
    }

    fun forceWriteAgentConfigs() {
        val s = synchronized(stateLock) { server } ?: return
        val t = synchronized(stateLock) { token } ?: return
        val wp = synchronized(workspacePathsLock) { workspacePathForResolution() }
        DiscoveryFileWriter.forceWriteAgentConfigs(
            DiscoveryFileWriter.BridgeInfo(host, s.port, t, wp)
        )
    }

    private fun clearDiscoveryFilesForProcess() {
        DiscoveryFileWriter.clearForProcess()
    }
}