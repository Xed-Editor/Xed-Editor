package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GetGitStatusTool : BaseMcpTool() {
    override fun getName(): String = "getGitStatus"
    override fun getDescription(): String = "Returns the git working tree status."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val result = ideService.getGitStatus(path)
        return jsonResult(result)
    }
}

class GetGitDiffTool : BaseMcpTool() {
    override fun getName(): String = "getGitDiff"
    override fun getDescription(): String = "Returns the unstaged git diff for the workspace."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val diff = ideService.getGitDiff(path)
        return textResult(diff)
    }
}
