package com.rk.ai.bridge.stitch

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    fun convertToNewConfigJson(config: ExternalMcpConfig): String {
        val entries = config.mcpServers.entries.map { (name, cfg) ->
            buildString {
                append("{\"type\":\"streamable_http\",\"id\":\"")
                append(java.util.UUID.randomUUID())
                append("\",\"commonOptions\":{\"enable\":")
                append(cfg.enabled)
                append(",\"name\":\"")
                append(name.replace("\"", "\\\""))
                append("\",\"headers\":[")
                cfg.headers.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) append(",")
                    append("[\"")
                    append(k.replace("\"", "\\\""))
                    append("\",\"")
                    append(v.replace("\"", "\\\""))
                    append("\"]")
                }
                append("],\"tools\":[]}")
                append(",\"url\":\"")
                append(cfg.url.replace("\"", "\\\""))
                append("\"}")
            }
        }
        return "[${entries.joinToString(",")}]"
    }

    fun convertFromNewConfigJson(jsonText: String): ExternalMcpConfig {
        if (jsonText.isBlank()) return ExternalMcpConfig()
        return try {
            val arr = json.parseToJsonElement(jsonText).jsonArray
            val servers = mutableMapOf<String, ExternalMcpServerConfig>()
            for (element in arr) {
                val obj = element.jsonObject
                val common = obj["commonOptions"]?.jsonObject ?: continue
                val name = common["name"]?.jsonPrimitive?.content ?: continue
                val url = obj["url"]?.jsonPrimitive?.content ?: continue
                val apiKey = extractApiKeyFromHeaders(common["headers"]?.jsonArray)
                val headers = extractHeadersFromArray(common["headers"]?.jsonArray)
                val enabled = common["enable"]?.jsonPrimitive?.boolean ?: true
                servers[name] = ExternalMcpServerConfig(
                    name = name,
                    url = url,
                    apiKey = apiKey,
                    headers = headers,
                    enabled = enabled,
                )
            }
            ExternalMcpConfig(mcpServers = servers)
        } catch (_: Exception) {
            ExternalMcpConfig()
        }
    }

    private fun extractApiKeyFromHeaders(headers: kotlinx.serialization.json.JsonArray?): String? {
        if (headers == null) return null
        for (entry in headers) {
            val arr = entry.jsonArray
            if (arr.size >= 2 && arr[0].jsonPrimitive.content.equals("Authorization", ignoreCase = true)) {
                val value = arr[1].jsonPrimitive.content
                if (value.startsWith("Bearer ")) return value.removePrefix("Bearer ")
            }
        }
        return null
    }

    private fun extractHeadersFromArray(headers: kotlinx.serialization.json.JsonArray?): Map<String, String> {
        if (headers == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (entry in headers) {
            val arr = entry.jsonArray
            if (arr.size >= 2) {
                val key = arr[0].jsonPrimitive.content
                val value = arr[1].jsonPrimitive.content
                if (!key.equals("Authorization", ignoreCase = true)) {
                    map[key] = value
                }
            }
        }
        return map
    }
}
