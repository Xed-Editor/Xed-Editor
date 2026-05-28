package com.rk.ai.bridge

import com.google.gson.JsonArray
import com.google.gson.JsonObject

interface McpTool {
    fun getName(): String
    fun getDescription(): String
    fun getRequiredParams(): Map<String, String> = emptyMap()
    fun getOptionalParams(): Map<String, String> = emptyMap()
    fun getRequiredParamDescriptions(): Map<String, String> = emptyMap()
    fun getOptionalParamDescriptions(): Map<String, String> = emptyMap()
    fun getTimeoutMs(): Long = 60_000L

    suspend fun execute(args: JsonObject, context: McpToolContext): McpToolResult

    fun getSchema(): JsonObject = JsonObject().apply {
        addProperty("name", getName())
        addProperty("description", getDescription())
        add("inputSchema", JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                val allParams = linkedMapOf<String, String>()
                allParams.putAll(getRequiredParams())
                allParams.putAll(getOptionalParams())
                val allDescriptions = linkedMapOf<String, String>()
                allDescriptions.putAll(getRequiredParamDescriptions())
                allDescriptions.putAll(getOptionalParamDescriptions())
                allParams.forEach { (name, type) ->
                    add(name, JsonObject().apply {
                        addProperty("type", type)
                        allDescriptions[name]?.let { addProperty("description", it) }
                    })
                }
            })
            val required = getRequiredParams().keys
            if (required.isNotEmpty()) {
                add("required", JsonArray().apply { required.forEach { add(it) } })
            }
        })
    }
}

class McpToolRegistry {
    private val tools = mutableMapOf<String, McpTool>()

    fun register(tool: McpTool) {
        tools[tool.getName()] = tool
    }

    suspend fun execute(name: String, args: JsonObject, context: McpToolContext): McpToolResult? {
        val tool = tools[name] ?: return null
        return tool.execute(args, context)
    }

    fun get(name: String): McpTool? = tools[name]

    fun listNames(): Set<String> = tools.keys

    fun listSchemas(): JsonArray = JsonArray().apply {
        tools.values.forEach { add(it.getSchema()) }
    }

    fun getTimeoutMs(name: String): Long = tools[name]?.getTimeoutMs() ?: 60_000L
}
