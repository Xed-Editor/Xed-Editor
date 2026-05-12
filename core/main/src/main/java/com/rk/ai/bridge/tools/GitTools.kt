package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService

class GetGitStatusTool : McpTool {
    override fun getName(): String = "getGitStatus"
    override fun getDescription(): String = "Returns the git working tree status."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val path = args.get("path")?.asString
            ?: ideService.getPrimaryWorkspacePath()
        val result = ideService.getGitStatus(path)
        return jsonResult(result)
    }
}
