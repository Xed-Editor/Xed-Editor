package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GetGitStatusTool : BaseMcpTool() {
    override fun getName(): String = "getGitStatus"
    override fun getDescription(): String = "Returns the git status (staged, modified, untracked files). Use this to see what work has already been done or to prepare for a commit."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val result = ideService.getGitStatus(path)
        return jsonResult(result)
    }
}

class GetGitDiffTool : BaseMcpTool() {
    override fun getName(): String = "getGitDiff"
    override fun getDescription(): String = "Returns the unstaged diff for the repository. Use this to review exactly what code has changed in the working tree."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val diff = ideService.getGitDiff(path)
        return textResult(diff)
    }
}

class GitCommitTool : BaseMcpTool() {
    override fun getName(): String = "gitCommit"
    override fun getDescription(): String = "Commits staged changes to the repository. If 'all' is true, it automatically stages modified/deleted files (git commit -a)."
    override fun getRequiredParams(): Map<String, String> = mapOf("message" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string", "all" to "boolean")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val message = requireString(args, "message")
        val all = optionalBoolean(args, "all", true)
        val result = ideService.gitCommit(path, message, all)
        return textResult(result)
    }
}

class GitCheckoutTool : BaseMcpTool() {
    override fun getName(): String = "gitCheckout"
    override fun getDescription(): String = "Switches branches or restores working tree files. Use this to move between branches or undo changes to specific files."
    override fun getRequiredParams(): Map<String, String> = mapOf("target" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val target = requireString(args, "target")
        val result = ideService.gitCheckout(path, target)
        return textResult(result)
    }
}
