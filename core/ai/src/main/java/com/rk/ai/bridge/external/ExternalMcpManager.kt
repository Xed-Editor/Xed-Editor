package com.rk.ai.bridge.external

import android.util.Log
import com.rk.ai.AiConfig
import com.rk.settings.Settings
import com.rk.settings.mcp.ExternalMcpConfigLoader
import com.rk.settings.mcp.ExternalMcpServerConfig
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class ExternalMcpManager {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = CopyOnWriteArrayList<ExternalMcpClient>()
    private val schemas = CopyOnWriteArrayList<ExternalMcpToolSchema>()
    private var onToolsChanged: ((List<ExternalMcpTool>) -> Unit)? = null
    var onConfigChanged: ((configJson: String) -> Unit)? = null

    fun setOnToolsChanged(callback: (List<ExternalMcpTool>) -> Unit) {
        onToolsChanged = callback
    }

    val connectedServers: List<String> get() = clients.map { it.serverName }
    val toolSchemas: List<ExternalMcpToolSchema> get() = schemas.toList()

    fun connectAll(configJson: String) {
        val config = ExternalMcpConfigLoader.load(configJson)
        val enabled = config.mcpServers.filter { it.value.enabled }

        managerScope.launch {
            disconnectAll()

            if (enabled.isEmpty()) {
                onConfigChanged?.invoke(configJson)
                return@launch
            }

            val results = enabled.map { (name, cfg) ->
                async {
                    connectServer(name, cfg)
                }
            }
            results.awaitAll()
            onConfigChanged?.invoke(configJson)
            notifyToolsChanged()
        }
    }

    fun connectAllFromSettings() {
        connectAll(Settings.ai_mcp_servers_config)
    }

    fun connectFromConfigFile(workspacePath: String) {
        val configFile = File(workspacePath, AiConfig.Discovery.mcpExternalServersFile)
        if (!configFile.exists()) return
        val text = runCatching { configFile.readText() }.getOrNull() ?: return
        connectAll(text)
    }

    private suspend fun connectServer(name: String, cfg: ExternalMcpServerConfig) {
        d("Connecting to external MCP server '$name' at ${cfg.url}")
        val client = ExternalMcpClient(
            serverName = name,
            baseUrl = cfg.url,
            apiKey = cfg.apiKey,
            headers = cfg.headers,
            timeoutMs = cfg.timeoutMs,
        )

        if (!client.isReachable()) {
            Log.w(TAG, "MCP server '$name' at ${cfg.url} is not reachable")
            return
        }

        val tools = client.listTools()
        if (tools.isEmpty()) {
            Log.w(TAG, "MCP server '$name' returned no tools")
            return
        }

        clients.add(client)
        schemas.addAll(tools)
        d("Connected to MCP server '$name': ${tools.size} tools found")
        tools.forEach { t ->
            d("  tool: ${t.name} - ${t.description.take(60)}")
        }
    }

    fun buildTools(): List<ExternalMcpTool> {
        val clientMap = clients.associateBy { it.serverName }
        return schemas.mapNotNull { schema ->
            val client = clientMap[schema.serverName] ?: return@mapNotNull null
            ExternalMcpTool(schema, client)
        }
    }

    suspend fun disconnectAll() {
        clients.forEach { it.disconnect() }
        clients.clear()
        schemas.clear()
        onToolsChanged?.invoke(emptyList())
    }

    suspend fun disconnectServer(name: String) {
        val idx = clients.indexOfFirst { it.serverName == name }
        if (idx >= 0) {
            clients[idx].disconnect()
            clients.removeAt(idx)
            schemas.removeAll { it.serverName == name }
            notifyToolsChanged()
        }
    }

    fun refresh() {
        connectAll(Settings.ai_mcp_servers_config)
    }

    fun getCurrentConfigJson(): String = Settings.ai_mcp_servers_config

    fun getClientStatus(): List<Map<String, Any>> {
        return clients.map { client ->
            mapOf(
                "name" to client.serverName,
                "url" to client.baseUrl,
                "reachable" to client.isReachable(),
                "toolCount" to schemas.count { it.serverName == client.serverName },
            )
        }
    }

    private fun notifyToolsChanged() {
        val tools = buildTools()
        onToolsChanged?.invoke(tools)
    }

    private fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    companion object {
        private const val TAG = "ExternalMcpManager"
    }
}
