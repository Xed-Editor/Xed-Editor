package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class GetGitStatusTool : BaseMcpTool() {
    override val name: String = "getGitStatus"
    override val description: String = "Returns the git status (staged, modified, untracked files). Use this to see what work has already been done or to prepare for a commit."
    override val optionalParams: Map<String, String> = mapOf("path" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val path = optionalString(args, "path").ifBlank { context.ideService.getPrimaryWorkspacePath() }
        val result = context.ideService.getGitStatus(path)
        return resultJson(result)
    }
}

class GetGitDiffTool : BaseMcpTool() {
    override val name: String = "getGitDiff"
    override val description: String = "Returns the unstaged diff for the repository. Use this to review exactly what code has changed in the working tree."
    override val optionalParams: Map<String, String> = mapOf("path" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val path = optionalString(args, "path").ifBlank { context.ideService.getPrimaryWorkspacePath() }
        val diff = context.ideService.getGitDiff(path)
        return resultText(diff)
    }
}
