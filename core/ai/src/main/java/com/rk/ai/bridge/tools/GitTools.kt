package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class GetGitStatusTool : BaseMcpTool() {
    override fun getCategory(): String = "Git"
    override fun getName(): String = "getGitStatus"
    override fun getDescription(): String = "Returns the git status (staged, modified, untracked files)."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val path = optionalString(args, "path").ifBlank { context.ideService.getPrimaryWorkspacePath() }
        val result = context.ideService.getGitStatus(path)
        return McpToolResult.success(result.toString())
    }
}

class GetGitDiffTool : BaseMcpTool() {
    override fun getCategory(): String = "Git"
    override fun getName(): String = "getGitDiff"
    override fun getDescription(): String = "Returns the unstaged diff for the repository."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val path = optionalString(args, "path").ifBlank { context.ideService.getPrimaryWorkspacePath() }
        val diff = context.ideService.getGitDiff(path)
        return McpToolResult.success(diff)
    }
}

class GitCommitTool : BaseMcpTool() {
    override fun getCategory(): String = "Git"
    override fun getName(): String = "gitCommit"
    override fun getDescription(): String = "Commits staged changes to the repository. If 'all' is true, it auto-stages."
    override fun getRequiredParams(): Map<String, String> = mapOf("message" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string", "all" to "boolean")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "message" to "Commit message"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)",
        "all" to "Auto-stage all modified/deleted files before committing (default: false)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val path = optionalString(args, "path").ifBlank { context.ideService.getPrimaryWorkspacePath() }
        val message = requireString(args, "message")
        val all = optionalBoolean(args, "all")
        val result = context.ideService.gitCommit(path, message, all)
        return McpToolResult.success(result)
    }
}

class GitCheckoutTool : BaseMcpTool() {
    override fun getCategory(): String = "Git"
    override fun getName(): String = "gitCheckout"
    override fun getDescription(): String = "Switches branches or restores working tree files."
    override fun getRequiredParams(): Map<String, String> = mapOf("target" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "target" to "Branch name or commit hash to switch to"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val path = optionalString(args, "path").ifBlank { context.ideService.getPrimaryWorkspacePath() }
        val target = requireString(args, "target")
        val result = context.ideService.gitCheckout(path, target)
        return McpToolResult.success(result)
    }
}
