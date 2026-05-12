package com.rk.ai.bridge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

interface McpTool {
    fun getName(): String
    fun getDescription(): String
    fun getRequiredParams(): Map<String, String> = emptyMap()
    fun getOptionalParams(): Map<String, String> = emptyMap()
    suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject

    fun getSchema(): JsonObject = JsonObject().apply {
        addProperty("name", getName())
        addProperty("description", getDescription())
        add("inputSchema", JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                getRequiredParams().forEach { (name, type) ->
                    add(name, JsonObject().apply { addProperty("type", type) })
                }
                getOptionalParams().forEach { (name, type) ->
                    add(name, JsonObject().apply { addProperty("type", type) })
                }
            })
            add("required", JsonArray().apply { getRequiredParams().keys.forEach { add(it) } })
        })
    }
}

class McpToolRegistry(private val ideService: IdeService) {
    private val tools = mutableMapOf<String, McpTool>()

    fun register(tool: McpTool) {
        tools[tool.getName()] = tool
    }

    suspend fun execute(name: String, args: JsonObject): JsonObject? {
        return tools[name]?.execute(args, ideService)
    }

    fun listNames(): Set<String> = tools.keys

    fun listSchemas(): JsonArray = JsonArray().apply {
        tools.values.forEach { add(it.getSchema()) }
    }
}
