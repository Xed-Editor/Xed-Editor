package com.rk.ai.bridge

import com.google.gson.JsonObject
import com.rk.ai.service.GeminiIdeService

interface McpTool {
    fun getName(): String
    suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject
}

class McpToolRegistry(private val ideService: GeminiIdeService) {
    private val tools = mutableMapOf<String, McpTool>()

    fun register(tool: McpTool) {
        tools[tool.getName()] = tool
    }

    suspend fun execute(name: String, args: JsonObject): JsonObject? {
        return tools[name]?.execute(args, ideService)
    }

    fun listNames(): Set<String> = tools.keys
}
