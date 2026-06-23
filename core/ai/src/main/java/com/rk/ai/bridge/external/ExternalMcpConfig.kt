package com.rk.ai.bridge.external

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ExternalMcpServerConfig(
    val name: String,
    val url: String,
    val apiKey: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val timeoutMs: Long = 60_000L,
)

@Serializable
data class ExternalMcpConfig(
    val mcpServers: Map<String, ExternalMcpServerConfig> = emptyMap(),
)

object ExternalMcpConfigLoader {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(jsonText: String): ExternalMcpConfig {
        if (jsonText.isBlank()) return ExternalMcpConfig()
        return try {
            json.decodeFromString<ExternalMcpConfig>(jsonText)
        } catch (_: Exception) {
            ExternalMcpConfig()
        }
    }

    fun save(config: ExternalMcpConfig): String = json.encodeToString(config)

    fun addServer(config: ExternalMcpConfig, server: ExternalMcpServerConfig): ExternalMcpConfig {
        val servers = config.mcpServers.toMutableMap()
        servers[server.name] = server
        return config.copy(mcpServers = servers)
    }

    fun removeServer(config: ExternalMcpConfig, name: String): ExternalMcpConfig {
        val servers = config.mcpServers.toMutableMap()
        servers.remove(name)
        return config.copy(mcpServers = servers)
    }
}
