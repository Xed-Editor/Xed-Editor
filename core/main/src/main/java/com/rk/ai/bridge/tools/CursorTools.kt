package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService

class GetSymbolUnderCursorTool : McpTool {
    override fun getName(): String = "getSymbolUnderCursor"
    override fun getDescription(): String = "Gets the symbol under the cursor in the active editor."
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val result = ideService.getSymbolUnderCursor()
        return jsonResult(result)
    }
}

class GetGitDiffTool : McpTool {
    override fun getName(): String = "getGitDiff"
    override fun getDescription(): String = "Returns the unstaged git diff for the workspace."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val path = args.get("path")?.asString ?: ideService.getPrimaryWorkspacePath()
        val diff = ideService.getGitDiff(path)
        return textResult(diff)
    }
}

class GetProjectConfigTool : McpTool {
    override fun getName(): String = "getProjectConfig"
    override fun getDescription(): String = "Detects project configuration."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val path = args.get("path")?.asString ?: ideService.getPrimaryWorkspacePath()
        val config = ideService.getProjectConfig(path)
        return jsonResult(config)
    }
}
