package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GetGitStatusTool : BaseMcpTool() {
    override fun getName(): String = "getGitStatus"
    override fun getDescription(): String = "Returns the git status (branch, staged, modified, untracked files, ahead/behind counts). Use this to see what work has already been done or to prepare for a commit."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val result = ideService.getGitStatus(path)
        return jsonResult(result)
    }
}

class GetGitDiffTool : BaseMcpTool() {
    override fun getName(): String = "getGitDiff"
    override fun getDescription(): String = "Returns the full diff (staged + unstaged changes) for the repository."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val diff = ideService.getGitDiff(path)
        return textResult(diff)
    }
}

class GitCommitTool : BaseMcpTool() {
    override fun getName(): String = "gitCommit"
    override fun getDescription(): String = "Commits staged changes to the repository. If 'all' is true, it automatically stages modified/deleted files (git commit -a). CRITICAL: Run getGitStatus first to review changes before committing."
    override fun getRequiredParams(): Map<String, String> = mapOf("message" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string", "all" to "boolean")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "message" to "Commit message"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)",
        "all" to "Auto-stage all modified/deleted files before committing (default: false)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val message = requireString(args, "message")
        val all = optionalBoolean(args, "all")
        val result = ideService.gitCommit(path, message, all)
        return textResult(result)
    }
}

class GitCheckoutTool : BaseMcpTool() {
    override fun getName(): String = "gitCheckout"
    override fun getDescription(): String = "Switches branches or creates and switches to a new branch. Use 'createNew: true' to create a new branch."
    override fun getRequiredParams(): Map<String, String> = mapOf("target" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string", "createNew" to "boolean")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "target" to "Branch name to switch to (or new branch name if createNew=true)"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)",
        "createNew" to "Create a new branch with the given name and switch to it"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val target = requireString(args, "target")
        val createNew = optionalBoolean(args, "createNew")
        val result = ideService.gitCheckout(path, target, createNew)
        return textResult(result)
    }
}

class GitLogTool : BaseMcpTool() {
    override fun getName(): String = "gitLog"
    override fun getDescription(): String = "Shows recent commit history with author, date, hash, and message."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string", "maxCount" to "number")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)",
        "maxCount" to "Maximum commits to show (default: 20, max: 200)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val maxCount = (optionalPositiveInt(args, "maxCount") ?: 20).coerceIn(1, 200)
        val result = ideService.gitLog(path, maxCount)
        return jsonResult(result)
    }
}

class ListGitBranchesTool : BaseMcpTool() {
    override fun getName(): String = "listGitBranches"
    override fun getDescription(): String = "Lists all local and remote git branches. Shows which branch is current. CRITICAL: Use this before checkout to see available branches."
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

class GitPullTool : BaseMcpTool() {
    override fun getName(): String = "gitPull"
    override fun getDescription(): String = "Pulls latest changes from remote. Optionally rebase instead of merge."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string", "rebase" to "boolean")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)",
        "rebase" to "Rebase instead of merge (default: false)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val rebase = optionalBoolean(args, "rebase")
        val result = ideService.gitPull(path, rebase)
        return textResult(result)
    }
}

class GitPushTool : BaseMcpTool() {
    override fun getName(): String = "gitPush"
    override fun getDescription(): String = "Pushes local commits to remote. CRITICAL: Use force push with caution — it rewrites remote history."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string", "force" to "boolean")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)",
        "force" to "Force push (overwrites remote history). Use with extreme caution."
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val force = optionalBoolean(args, "force")
        val result = ideService.gitPush(path, force)
        return textResult(result)
    }
}

class GitFetchTool : BaseMcpTool() {
    override fun getName(): String = "gitFetch"
    override fun getDescription(): String = "Fetches latest changes from remote without merging. Safe to run anytime."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val result = ideService.gitFetch(path)
        return textResult(result)
    }
}

class GitCreateBranchTool : BaseMcpTool() {
    override fun getName(): String = "gitCreateBranch"
    override fun getDescription(): String = "Creates a new branch from the current HEAD or a specific starting point."
    override fun getRequiredParams(): Map<String, String> = mapOf("name" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string", "startPoint" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "name" to "Name for the new branch"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)",
        "startPoint" to "Branch or commit to start from (default: current HEAD)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val branchName = requireString(args, "name")
        val startPoint = optionalString(args, "startPoint").ifBlank { null }
        val result = ideService.gitCreateBranch(path, branchName, startPoint)
        return textResult(result)
    }
}

class GitStashTool : BaseMcpTool() {
    override fun getName(): String = "gitStash"
    override fun getDescription(): String = "Stashes working directory changes. CRITICAL: Use this before switching branches or pulling when you have uncommitted changes."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string", "message" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)",
        "message" to "Optional stash description for identification"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val message = optionalString(args, "message").ifBlank { null }
        val result = ideService.gitStash(path, message)
        return textResult(result)
    }
}

class GitStashPopTool : BaseMcpTool() {
    override fun getName(): String = "gitStashPop"
    override fun getDescription(): String = "Restores the most recently stashed changes and removes them from the stash list."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Repository path (default: workspace root)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val result = ideService.gitStashPop(path)
        return textResult(result)
    }
}