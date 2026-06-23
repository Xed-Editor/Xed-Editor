package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.IdeBridge
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import com.rk.ai.bridge.stitch.ExternalMcpConfig
import com.rk.ai.bridge.stitch.ExternalMcpConfigLoader
import com.rk.ai.bridge.stitch.ExternalMcpServerConfig
import com.rk.settings.Settings

class McpStitcherConfigTool : BaseMcpTool() {
    override fun getCategory(): String = "MCP Stitcher"
    override fun getName(): String = "mcpStitcher"
    override fun getDescription(): String = "Manage external MCP server connections. Actions: list, add, remove, refresh."

    override fun getRequiredParams(): Map<String, String> = mapOf("action" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "name" to "string",
        "url" to "string",
        "apiKey" to "string",
        "headers" to "object",
        "enabled" to "boolean",
    )

    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "action" to "Action: 'list', 'add', 'remove', 'refresh'"
    )

    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "name" to "Server name (required for add/remove)",
        "url" to "MCP server URL (required for add, e.g. http://host:port)",
        "apiKey" to "Optional API key/token for authentication",
        "headers" to "Optional custom headers as JSON object (e.g. {\"X-Goog-Api-Key\": \"key\"})",
        "enabled" to "Whether this server is enabled (default: true)",
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val action = args.get("action")?.asString?.lowercase() ?: return McpToolResult.error("Missing 'action' param")
        return when (action) {
            "list" -> listServers()
            "add" -> addServer(args)
            "remove" -> removeServer(args)
            "refresh" -> refreshServers()
            else -> McpToolResult.error("Unknown action: $action. Use: list, add, remove, refresh")
        }
    }

    private fun listServers(): McpToolResult {
        val config = loadConfig()
        if (config.mcpServers.isEmpty()) {
            return McpToolResult.success("No external MCP servers configured.")
        }
        val lines = buildString {
            appendLine("Configured external MCP servers (${config.mcpServers.size}):")
            config.mcpServers.forEach { (name, cfg) ->
                appendLine("  $name")
                appendLine("    URL: ${cfg.url}")
                appendLine("    Auth: ${if (cfg.apiKey != null) "configured" else "none"}")
                appendLine("    Headers: ${cfg.headers.keys.joinToString(", ").ifEmpty { "none" }}")
                appendLine("    Enabled: ${cfg.enabled}")
                appendLine("    Timeout: ${cfg.timeoutMs}ms")
            }
            appendLine()
        }
        return McpToolResult.success(lines)
    }

    private fun addServer(args: JsonObject): McpToolResult {
        val name = args.get("name")?.asString?.takeIf { it.isNotBlank() }
            ?: return McpToolResult.error("Missing 'name' param")
        val url = args.get("url")?.asString?.takeIf { it.isNotBlank() }
            ?: return McpToolResult.error("Missing 'url' param")
        val apiKey = args.get("apiKey")?.asString?.takeIf { it.isNotBlank() }
        val enabled = if (args.has("enabled") && !args.get("enabled").isJsonNull) {
            args.get("enabled").asBoolean
        } else true

        val headers = mutableMapOf<String, String>()
        if (args.has("headers") && !args.get("headers").isJsonNull) {
            val headersObj = args.getAsJsonObject("headers")
            headersObj?.keySet()?.forEach { key ->
                headersObj.get(key)?.asString?.let { value ->
                    headers[key] = value
                }
            }
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return McpToolResult.error("URL must start with http:// or https://")
        }

        var config = loadConfig()
        config = ExternalMcpConfigLoader.addServer(config, ExternalMcpServerConfig(
            name = name, url = url, apiKey = apiKey, headers = headers, enabled = enabled
        ))
        saveConfig(config)
        IdeBridge.refreshStitcher()

        return McpToolResult.success("Added MCP server '$name' at $url and refreshed stitcher.")
    }

    private fun removeServer(args: JsonObject): McpToolResult {
        val name = args.get("name")?.asString?.takeIf { it.isNotBlank() }
            ?: return McpToolResult.error("Missing 'name' param")
        var config = loadConfig()
        if (name !in config.mcpServers) return McpToolResult.error("Server '$name' not found")
        config = ExternalMcpConfigLoader.removeServer(config, name)
        saveConfig(config)
        IdeBridge.refreshStitcher()
        return McpToolResult.success("Removed MCP server '$name'.")
    }

    private fun refreshServers(): McpToolResult {
        IdeBridge.refreshStitcher()
        val config = loadConfig()
        return McpToolResult.success("Refreshed MCP server connections. ${config.mcpServers.size} servers configured.")
    }

    private fun loadConfig(): ExternalMcpConfig {
        return ExternalMcpConfigLoader.load(Settings.ai_mcp_servers_config)
    }

    private fun saveConfig(config: ExternalMcpConfig) {
        Settings.ai_mcp_servers_config = ExternalMcpConfigLoader.save(config)
    }
}
