package com.rk.ai

import android.util.Log
import com.rk.activities.main.MainViewModel
import com.rk.ai.bridge.DiscoveryFileWriter
import com.rk.ai.bridge.server.IdeBridgeServer
import com.rk.ai.service.IdeServiceImpl
import com.rk.xededitor.BuildConfig
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object IdeBridge {
    data class Info(val port: Int, val token: String, val workspacePath: String, val host: String = "127.0.0.1")

    private var server: IdeBridgeServer? = null
    private var token: String? = null
    private var port: Int = 0
    private var host: String = "127.0.0.1"
    private val secureRandom = SecureRandom()
    private val workspacePaths = mutableListOf<String>()
    private val workspacePathsLock = Any()
    private val stateLock = Any()
    private var lastError: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    fun connectedClients(): Int = synchronized(stateLock) { server?.connectedClients ?: 0 }
    fun availableTools(): Int = synchronized(stateLock) { server?.toolsCount ?: 0 }
    fun isRunning(): Boolean = synchronized(stateLock) { server != null }
    fun getLastError(): String? = lastError
    fun clearLastError() { lastError = null }

    fun getBridgeInfo(): Info? = synchronized(stateLock) {
        val s = server ?: return@synchronized null
        val t = token ?: return@synchronized null
        Info(s.port, t, workspacePathForResolution(), host)
    }

    fun ensureStarted(viewModel: MainViewModel, workspacePath: String? = null): Info? {
        workspacePath?.let { setWorkspacePath(it) }

        val existingServer = synchronized(stateLock) {
            val s = server
            if (s != null) {
                val health = healthCheckInternal()
                if (!health) {
                    Log.w("IdeBridge", "Bridge unhealthy, restarting...")
                    stopInternalLocked()
                    null
                } else {
                    val t = token
                    val wp = workspacePathForResolution()
                    synchronized(workspacePathsLock) {
                        if (workspacePaths.isNotEmpty()) {
                            writeDiscoveryFile(host, s.port, t ?: return@synchronized null, wp)
                        }
                    }
                    s
                }
            } else null
        }
        if (existingServer != null) {
            val info = getBridgeInfo() ?: return null
            synchronized(workspacePathsLock) {
                if (workspacePaths.isNotEmpty()) {
                    writeDiscoveryFile(host, info.port, info.token, info.workspacePath)
                }
            }
            return info
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
                if (s.ideService is IdeServiceImpl) {
                    (s.ideService as IdeServiceImpl).attachNotificationSender(s)
                }
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
            synchronized(stateLock) { server = null; token = null; port = 0 }
        }.getOrNull()
    }

    fun restart(viewModel: MainViewModel): Info? {
        stop()
        return ensureStarted(viewModel)
    }

    fun healthCheck(): Boolean = healthCheckInternal()

    private fun healthCheckInternal(): Boolean {
        val s = synchronized(stateLock) { server } ?: return false
        return try {
            val request = Request.Builder()
                .url("http://$host:${s.port}/health")
                .get()
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    fun checkMcpConnection(): Pair<Boolean, String> {
        val info = getBridgeInfo() ?: return false to "Bridge not running"
        return try {
            val request = Request.Builder()
                .url("http://${info.host}:${info.port}/mcp-info")
                .addHeader("Authorization", "Bearer ${info.token}")
                .addHeader("x-ide-token", info.token)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val hasTools = body.contains("\"tools\"")
                    if (hasTools) {
                        val toolsCount = body.substringAfter("\"tools\":").substringBefore(",").trim()
                        Pair(true, "MCP connected: $toolsCount tools")
                    } else {
                        Pair(true, "MCP connected")
                    }
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Pair(false, "MCP responded with code ${response.code}: $errorBody")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Connection failed: ${e.message}")
        }
    }

    fun verifyMcpToolsAvailable(): Pair<Boolean, String> {
        val info = getBridgeInfo() ?: return false to "Bridge not running"
        return try {
            val jsonRequest = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
            val request = Request.Builder()
                .url("http://${info.host}:${info.port}/mcp")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${info.token}")
                .addHeader("x-ide-token", info.token)
                .post(jsonRequest.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.contains("\"result\"") && body.contains("\"tools\"")) {
                        val toolsCount = body.split("\"name\":").size - 1
                        Pair(true, "$toolsCount MCP tools available")
                    } else {
                        Pair(false, "MCP response missing tools: ${body.take(200)}")
                    }
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Pair(false, "MCP tools/list returned ${response.code}: $errorBody")
                }
            }
        } catch (e: Exception) {
            Pair(false, "MCP tools/list failed: ${e.message}")
        }
    }

    fun setWorkspacePath(path: String) {
        synchronized(stateLock) {
            synchronized(workspacePathsLock) {
                if (!workspacePaths.contains(path)) {
                    workspacePaths.add(path)
                }
                val s = server
                val t = token
                if (s != null && t != null) {
                    writeDiscoveryFile(host, s.port, t, workspacePathForResolution())
                }
            }
        }
    }

    fun stop() {
        synchronized(stateLock) { stopInternalLocked() }
        synchronized(workspacePathsLock) { workspacePaths.clear() }
        clearDiscoveryFilesForProcess()
    }

    private fun stopInternalLocked() {
        Log.d("IdeBridge", "Stopping server")
        server?.stop()
        server = null
        token = null
        port = 0
    }

    fun primaryWorkspacePath(): String = synchronized(workspacePathsLock) { workspacePaths.lastOrNull().orEmpty() }

    fun workspacePathForResolution(): String = synchronized(workspacePathsLock) { workspacePaths.joinToString(File.pathSeparator) }

    fun forceWriteAgentConfigs() {
        synchronized(stateLock) {
            val s = server ?: return
            val t = token ?: return
            val wp = synchronized(workspacePathsLock) { workspacePathForResolution() }
            DiscoveryFileWriter.forceWriteAgentConfigs(
                DiscoveryFileWriter.BridgeInfo(host, s.port, t, wp)
            )
        }
    }

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

    private fun clearDiscoveryFilesForProcess() {
        DiscoveryFileWriter.clearForProcess()
    }
}
