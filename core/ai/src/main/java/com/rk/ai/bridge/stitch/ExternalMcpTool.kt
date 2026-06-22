package com.rk.ai.bridge.stitch

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class ExternalMcpTool(
    private val schema: ExternalMcpToolSchema,
    private val client: ExternalMcpClient,
) : McpTool {
    override fun getName(): String = "stitch_${schema.serverName}_${schema.name}"
    override fun getDescription(): String = "[${schema.serverName}] ${schema.description}"
    override fun getCategory(): String = "External MCP ($serverName)"
    override fun getTimeoutMs(): Long = client.timeoutMs

    private val serverName: String get() = schema.serverName

    override fun getRequiredParams(): Map<String, String> {
        val required = schema.inputSchema.getAsJsonArray("required") ?: JsonArray()
        val props = schema.inputSchema.getAsJsonObject("properties") ?: JsonObject()
        val map = linkedMapOf<String, String>()
        required.forEach { el ->
            val name = el.asString
            val prop = props.getAsJsonObject(name)
            val type = prop?.get("type")?.asString ?: "string"
            map[name] = type
        }
        return map
    }

    override fun getOptionalParams(): Map<String, String> {
        val required = schema.inputSchema.getAsJsonArray("required") ?: JsonArray()
        val requiredSet = required.map { it.asString }.toSet()
        val props = schema.inputSchema.getAsJsonObject("properties") ?: JsonObject()
        val map = linkedMapOf<String, String>()
        props.keySet().forEach { name ->
            if (name !in requiredSet) {
                val prop = props.getAsJsonObject(name)
                val type = prop?.get("type")?.asString ?: "string"
                map[name] = type
            }
        }
        return map
    }

    override fun getRequiredParamDescriptions(): Map<String, String> {
        val required = schema.inputSchema.getAsJsonArray("required") ?: JsonArray()
        val props = schema.inputSchema.getAsJsonObject("properties") ?: JsonObject()
        val map = linkedMapOf<String, String>()
        required.forEach { el ->
            val name = el.asString
            val prop = props.getAsJsonObject(name)
            val desc = prop?.get("description")?.asString ?: ""
            map[name] = desc
        }
        return map
    }

    override fun getOptionalParamDescriptions(): Map<String, String> {
        val required = schema.inputSchema.getAsJsonArray("required") ?: JsonArray()
        val requiredSet = required.map { it.asString }.toSet()
        val props = schema.inputSchema.getAsJsonObject("properties") ?: JsonObject()
        val map = linkedMapOf<String, String>()
        props.keySet().forEach { name ->
            if (name !in requiredSet) {
                val prop = props.getAsJsonObject(name)
                val desc = prop?.get("description")?.asString ?: ""
                map[name] = desc
            }
        }
        return map
    }

    override suspend fun execute(args: JsonObject, context: McpToolContext): McpToolResult {
        val start = System.currentTimeMillis()
        context.sendProgress("Forwarding to external MCP server '$serverName'...")

        val arguments = JsonObject()
        args.keySet().forEach { key -> arguments.add(key, args.get(key)) }

        val result = client.callTool(schema.name, arguments)
        val duration = System.currentTimeMillis() - start

        return if (result.success) {
            McpToolResult.success(
                result.output,
                mapOf(
                    "server" to serverName,
                    "tool" to schema.name,
                    "durationMs" to duration,
                ),
                duration,
            )
        } else {
            McpToolResult.error(
                "[$serverName/${schema.name}] ${result.error}",
                mapOf("server" to serverName, "tool" to schema.name),
                duration,
            )
        }
    }
}
