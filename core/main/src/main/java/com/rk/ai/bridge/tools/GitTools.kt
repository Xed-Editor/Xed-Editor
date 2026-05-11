package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.GeminiIdeService

class GetGitStatusTool : McpTool {
    override fun getName(): String = "getGitStatus"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val path = args.get("path")?.asString
            ?: ideService.getPrimaryWorkspacePath()
        val result = ideService.getGitStatus(path)
        return jsonResult(result)
    }
}
