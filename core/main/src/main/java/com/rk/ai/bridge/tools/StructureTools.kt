package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GetProjectStructureTool : BaseMcpTool() {
    override fun getName(): String = "getProjectStructure"
    override fun getDescription(): String = "Returns a hierarchical project directory tree."
    override fun getRequiredParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("maxDepth" to "number", "maxItems" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val maxDepth = optionalInt(args, "maxDepth", 3).coerceIn(1, 10)
        val maxItems = optionalInt(args, "maxItems", 200).coerceIn(1, 1000)
        val tree = ideService.getProjectStructure(path, maxDepth, maxItems)
        return textResult(tree)
    }
}
