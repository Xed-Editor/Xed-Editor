package com.rk.ai.mcp

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class McpServerConfig(
    val id: String,
    val name: String,
    val type: McpServerType,
    val url: String = "",
    val command: String = "",
    val args: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val timeoutMs: Long = 60000L,
)

enum class McpServerType { REMOTE, LOCAL, EMBEDDED }

data class McpConnectionState(
    val serverId: String,
    val status: ConnectionStatus,
    val tools: List<String> = emptyList(),
    val connectedAt: Long = 0,
    val lastError: String? = null,
    val reconnectAttempts: Int = 0,
    val latencyMs: Long = 0,
)

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR, RECONNECTING
}

data class ToolExecutionResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val executionTimeMs: Long = 0,
)

sealed class McpEvent {
    data class ConnectionChanged(val serverId: String, val status: ConnectionStatus) : McpEvent()
    data class ToolExecuted(val serverId: String, val toolName: String, val result: ToolExecutionResult) : McpEvent()
    data class Error(val serverId: String, val message: String) : McpEvent()
    data class Log(val serverId: String, val level: String, val message: String) : McpEvent()
}

class McpManager(
    private val ideService: IdeService? = null,
) {
    private val gson = GsonBuilder().create()
    private val TAG = "McpManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val servers = ConcurrentHashMap<String, McpServerConfig>()
    private val connectionStates = ConcurrentHashMap<String, McpConnectionState>()
    private val eventListeners = mutableListOf<(McpEvent) -> Unit>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val reconnectJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    fun registerServer(config: McpServerConfig) {
        servers[config.id] = config
        connectionStates[config.id] = McpConnectionState(
            serverId = config.id,
            status = ConnectionStatus.DISCONNECTED,
        )
        if (config.enabled) {
            connectToServer(config.id)
        }
    }

    fun removeServer(serverId: String) {
        disconnectServer(serverId)
        servers.remove(serverId)
        connectionStates.remove(serverId)
    }

    fun connectToServer(serverId: String) {
        val config = servers[serverId] ?: return
        updateState(serverId, ConnectionStatus.CONNECTING)

        scope.launch {
            try {
                when (config.type) {
                    McpServerType.REMOTE -> connectRemote(serverId, config)
                    McpServerType.EMBEDDED -> connectEmbedded(serverId, config)
                    McpServerType.LOCAL -> connectLocal(serverId, config)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed for $serverId", e)
                updateState(serverId, ConnectionStatus.ERROR, lastError = e.message)
                notifyListeners(McpEvent.Error(serverId, e.message ?: "Connection failed"))
                scheduleReconnect(serverId)
            }
        }
    }

    fun disconnectServer(serverId: String) {
        reconnectJobs[serverId]?.cancel()
        reconnectJobs.remove(serverId)
        updateState(serverId, ConnectionStatus.DISCONNECTED)
    }

    suspend fun executeTool(serverId: String, toolName: String, args: JsonObject): ToolExecutionResult {
        val state = connectionStates[serverId]
            ?: return ToolExecutionResult(false, "", "Server $serverId not found")
        if (state.status != ConnectionStatus.CONNECTED) {
            return ToolExecutionResult(false, "", "Server $serverId not connected")
        }

        val config = servers[serverId] ?: return ToolExecutionResult(false, "", "Server config not found")
        val startTime = System.currentTimeMillis()

        return try {
            val result = withContext(Dispatchers.IO) {
                withTimeout(config.timeoutMs) {
                    executeToolCall(serverId, config, toolName, args)
                }
            }
            val elapsed = System.currentTimeMillis() - startTime
            val executionResult = ToolExecutionResult(
                success = true,
                output = result,
                executionTimeMs = elapsed,
            )
            notifyListeners(McpEvent.ToolExecuted(serverId, toolName, executionResult))
            executionResult
        } catch (e: TimeoutCancellationException) {
            val elapsed = System.currentTimeMillis() - startTime
            ToolExecutionResult(false, "", "Tool '$toolName' timed out after ${config.timeoutMs}ms", elapsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            ToolExecutionResult(false, "", "${e::class.java.simpleName}: ${e.message}", elapsed)
        }
    }

    fun getState(serverId: String): McpConnectionState? = connectionStates[serverId]

    fun getAllStates(): List<McpConnectionState> = connectionStates.values.toList()

    fun getTools(serverId: String): List<String> =
        connectionStates[serverId]?.tools ?: emptyList()

    fun onEvent(listener: (McpEvent) -> Unit) {
        eventListeners.add(listener)
    }

    fun removeListener(listener: (McpEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    fun shutdown() {
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        servers.keys.forEach { disconnectServer(it) }
        servers.clear()
        connectionStates.clear()
        eventListeners.clear()
    }

    fun diagnosticReport(): JsonObject = JsonObject().apply {
        addProperty("totalServers", servers.size)
        addProperty("connectedServers", connectionStates.values.count { it.status == ConnectionStatus.CONNECTED })
        add("servers", JsonArray().apply {
            connectionStates.values.forEach { state ->
                add(JsonObject().apply {
                    addProperty("id", state.serverId)
                    addProperty("status", state.status.name)
                    addProperty("tools", state.tools.size)
                    addProperty("connectedAt", state.connectedAt)
                    addProperty("reconnectAttempts", state.reconnectAttempts)
                    state.lastError?.let { addProperty("lastError", it) }
                })
            }
        })
    }

    private suspend fun connectRemote(serverId: String, config: McpServerConfig) {
        val request = Request.Builder()
            .url(config.url)
            .addHeader("Content-Type", "application/json")
            .apply { config.headers.forEach { (k, v) -> addHeader(k, v) } }
            .get()
            .build()

        val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
        if (!response.isSuccessful) {
            throw Exception("Server returned ${response.code}")
        }
        val body = response.body?.string() ?: ""

        val tools = if (body.contains("\"tools\"")) {
            extractToolNames(body)
        } else {
            // Do an explicit tools/list request
            val toolsResponse = fetchToolList(serverId, config)
            response.body?.close()
            toolsResponse
        }

        updateState(serverId, ConnectionStatus.CONNECTED, tools = tools)
        notifyListeners(McpEvent.ConnectionChanged(serverId, ConnectionStatus.CONNECTED))
    }

    private suspend fun connectEmbedded(serverId: String, config: McpServerConfig) {
        val tools = getEmbeddedToolNames()
        updateState(serverId, ConnectionStatus.CONNECTED, tools = tools)
        notifyListeners(McpEvent.ConnectionChanged(serverId, ConnectionStatus.CONNECTED))
    }

    private suspend fun connectLocal(serverId: String, config: McpServerConfig) {
        val process = ProcessBuilder(config.command, *config.args.toTypedArray())
            .redirectErrorStream(true)
            .start()

        val tools = listOf<String>() // Local process tools TBD
        updateState(serverId, ConnectionStatus.CONNECTED, tools = tools)
        notifyListeners(McpEvent.ConnectionChanged(serverId, ConnectionStatus.CONNECTED))

        // Monitor process
        scope.launch {
            try {
                val exitCode = withContext(Dispatchers.IO) { process.waitFor() }
                if (exitCode != 0) {
                    updateState(serverId, ConnectionStatus.ERROR, lastError = "Process exited with code $exitCode")
                    notifyListeners(McpEvent.Error(serverId, "Process exited with code $exitCode"))
                    scheduleReconnect(serverId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Process monitor error for $serverId", e)
            }
        }
    }

    private suspend fun executeToolCall(serverId: String, config: McpServerConfig, toolName: String, args: JsonObject): String {
        return when (config.type) {
            McpServerType.REMOTE -> executeRemoteTool(serverId, config, toolName, args)
            McpServerType.EMBEDDED -> executeEmbeddedTool(serverId, toolName, args)
            McpServerType.LOCAL -> executeLocalTool(serverId, config, toolName, args)
        }
    }

    private suspend fun executeRemoteTool(serverId: String, config: McpServerConfig, toolName: String, args: JsonObject): String {
        val requestBody = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", 1)
            addProperty("method", "tools/call")
            add("params", JsonObject().apply {
                addProperty("name", toolName)
                add("arguments", args)
            })
        }

        val request = Request.Builder()
            .url("${config.url.trimEnd('/')}/mcp")
            .addHeader("Content-Type", "application/json")
            .apply { config.headers.forEach { (k, v) -> addHeader(k, v) } }
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw Exception("Tool call failed: ${response.code} ${body.take(200)}")
        }
        return body
    }

    private suspend fun executeEmbeddedTool(serverId: String, toolName: String, args: JsonObject): String {
        val embeddedService = ideService
        if (embeddedService == null) {
            return """{"error":"Embedded IDE service not available"}"""
        }

        // Look up the tool from the embedded registry
        val tool = findEmbeddedTool(toolName)
        if (tool == null) {
            return """{"error":"Tool $toolName not available in embedded server"}"""
        }

        return try {
            val result = withContext(Dispatchers.Default) {
                tool.execute(args, embeddedService)
            }
            gson.toJson(JsonObject().apply {
                add("result", result)
            })
        } catch (e: Exception) {
            gson.toJson(JsonObject().apply {
                add("error", JsonObject().apply {
                    addProperty("code", -32603)
                    addProperty("message", e.message ?: "Execution failed")
                })
            })
        }
    }

    private suspend fun executeLocalTool(serverId: String, config: McpServerConfig, toolName: String, args: JsonObject): String {
        return """{"error":"Local tool execution not yet implemented"}"""
    }

    private suspend fun fetchToolList(serverId: String, config: McpServerConfig): List<String> {
        val requestBody = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        val request = Request.Builder()
            .url("${config.url.trimEnd('/')}/mcp")
            .addHeader("Content-Type", "application/json")
            .apply { config.headers.forEach { (k, v) -> addHeader(k, v) } }
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            extractToolNames(body)
        }
    }

    private fun extractToolNames(json: String): List<String> {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val result = root.getAsJsonObject("result")
            val tools = result?.getAsJsonArray("tools") ?: root.getAsJsonArray("tools")
            val names = mutableListOf<String>()
            tools?.forEach { tool ->
                tool.asJsonObject.get("name")?.asString?.let { names.add(it) }
            }
            names
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getEmbeddedToolNames(): List<String> {
        return com.rk.ai.bridge.McpToolRegistry::class.java
            .methods
            .filter { it.name.startsWith("get") || it.name.startsWith("list") }
            .map { it.name }
            .ifEmpty { listOf("readFile", "writeFile", "searchCode", "runCommand", "gitStatus", "listFiles") }
    }

    private fun findEmbeddedTool(name: String): McpTool? = null // TBD

    private fun updateState(serverId: String, status: ConnectionStatus, tools: List<String>? = null, lastError: String? = null) {
        val current = connectionStates[serverId] ?: return
        connectionStates[serverId] = current.copy(
            status = status,
            tools = tools ?: current.tools,
            connectedAt = if (status == ConnectionStatus.CONNECTED) System.currentTimeMillis() else current.connectedAt,
            lastError = lastError ?: if (status == ConnectionStatus.CONNECTED) null else current.lastError,
            reconnectAttempts = if (status == ConnectionStatus.CONNECTED) 0 else current.reconnectAttempts,
        )
    }

    private fun scheduleReconnect(serverId: String) {
        if (reconnectJobs.containsKey(serverId)) return
        val config = servers[serverId] ?: return
        if (!config.enabled) return

        reconnectJobs[serverId] = scope.launch {
            val baseDelay = 5000L
            val maxDelay = 60000L
            var attempt = 0

            while (isActive) {
                attempt++
                val state = connectionStates[serverId] ?: break
                if (state.status == ConnectionStatus.CONNECTED) break

                val delay = (baseDelay * (1L shl attempt.coerceAtMost(4))).coerceAtMost(maxDelay)
                updateState(serverId, ConnectionStatus.RECONNECTING, lastError = "Reconnecting in ${delay}ms (attempt $attempt)")

                delay(delay)

                try {
                    connectToServer(serverId)
                    val newState = connectionStates[serverId]
                    if (newState?.status == ConnectionStatus.CONNECTED) break
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect attempt $attempt for $serverId failed: ${e.message}")
                }
            }

            reconnectJobs.remove(serverId)
        }
    }

    private fun notifyListeners(event: McpEvent) {
        eventListeners.forEach { it(event) }
    }

    fun log(serverId: String, level: String, message: String) {
        notifyListeners(McpEvent.Log(serverId, level, message))
    }
}

object McpRegistry {
    private val managers = ConcurrentHashMap<String, McpManager>()

    fun get(instanceId: String = "default"): McpManager =
        managers.getOrPut(instanceId) { McpManager() }

    fun remove(instanceId: String) {
        managers.remove(instanceId)?.shutdown()
    }

    fun shutdown() {
        managers.values.forEach { it.shutdown() }
        managers.clear()
    }
}
