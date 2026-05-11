package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.GeminiIdeService

class GetProjectStructureTool : McpTool {
    override fun getName(): String = "getProjectStructure"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val path = args.get("path")?.asString
            ?: ideService.getPrimaryWorkspacePath()
        val maxDepth = args.get("maxDepth")?.asInt ?: 3
        val maxItems = args.get("maxItems")?.asInt ?: 200
        val tree = ideService.getProjectStructure(path, maxDepth.coerceIn(1, 10), maxItems.coerceIn(1, 1000))
        return textResult(tree)
    }
}
