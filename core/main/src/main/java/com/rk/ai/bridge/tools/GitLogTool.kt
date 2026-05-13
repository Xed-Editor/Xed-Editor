package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GitLogTool : BaseMcpTool() {
    override fun getName(): String = "gitLog"
    override fun getDescription(): String =
        "Shows recent commit history for the repository. " +
        "Use this to understand what changes have been made recently, find when a bug was introduced, " +
        "or review the commit history before creating a PR."

    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "maxCount" to "number"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)",
        "maxCount" to "Maximum number of commits to show (default: 20)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val maxCount = (optionalPositiveInt(args, "maxCount") ?: 20).coerceIn(1, 200)
        val result = ideService.gitLog(path, maxCount)
        return jsonResult(result)
    }
}
