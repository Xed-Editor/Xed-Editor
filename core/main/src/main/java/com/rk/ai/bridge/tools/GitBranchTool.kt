package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class ListGitBranchesTool : BaseMcpTool() {
    override fun getName(): String = "listGitBranches"
    override fun getDescription(): String =
        "Lists all local and remote git branches. " +
        "Use this before switch branches or to understand the branching strategy. " +
        "Shows which branch is currently checked out."

    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val result = ideService.listGitBranches(path)
        return jsonResult(result)
    }
}
