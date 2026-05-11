package com.rk.ai.bridge

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

interface McpTool {
    fun getName(): String
    suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject
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
}
