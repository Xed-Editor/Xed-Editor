package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService

class GetProjectStructureTool : McpTool {
    override fun getName(): String = "getProjectStructure"
    override fun getDescription(): String = "Returns a hierarchical project directory tree."
    override fun getRequiredParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("maxDepth" to "number", "maxItems" to "number")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val path = args.get("path")?.asString
            ?: ideService.getPrimaryWorkspacePath()
        val maxDepth = args.get("maxDepth")?.asInt ?: 3
        val maxItems = args.get("maxItems")?.asInt ?: 200
        val tree = ideService.getProjectStructure(path, maxDepth.coerceIn(1, 10), maxItems.coerceIn(1, 1000))
        return textResult(tree)
    }
}
