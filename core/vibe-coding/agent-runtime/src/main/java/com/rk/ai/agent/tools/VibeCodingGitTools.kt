@file:OptIn(ExperimentalUuidApi::class)

package com.rk.ai.agent.tools

import com.google.gson.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService

class VibeCodingGitTools(private val ideService: IdeService) {

    private fun com.google.gson.JsonElement.workspaceOrPrimary(): String =
        asJsonObject["workspacePath"]?.asJsonPrimitive?.asString ?: ideService.getPrimaryWorkspacePath()

    private val getGitStatus = Tool(
        name = "getGitStatus",
        description = "Returns the git status (staged, modified, untracked files) for the workspace.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("workspacePath") { put("type", "string"); put("description", "Path to the git repository (optional, uses primary workspace if not set)") }
                },
                required = emptyList<String>(),
            )
        },
        execute = { args ->
            val workspace = args.workspaceOrPrimary()
            val status = ideService.getGitStatus(workspace)
            val text = buildString {
                status.keySet().forEach { key ->
                    val element = status.get(key)
                    if (element is com.google.gson.JsonArray) {
                        if (element.size() > 0) {
                            appendLine("$key:")
                            element.forEach { appendLine("  ${it.asString}") }
                        }
                    } else {
                        appendLine("$key: ${element.asString}")
                    }
                }
            }
            listOf(UIMessagePart.Text(text.ifEmpty { "Working tree clean" }))
        },
    )

    private val getGitDiff = Tool(
        name = "getGitDiff",
        description = "Returns the unstaged diff for the repository.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("workspacePath") { put("type", "string"); put("description", "Path to the git repository (optional)") }
                },
                required = emptyList<String>(),
            )
        },
        execute = { args ->
            val workspace = args.workspaceOrPrimary()
            val diff = ideService.getGitDiff(workspace)
            listOf(UIMessagePart.Text(diff.ifEmpty { "No unstaged changes" }))
        },
    )

    private val gitCommit = Tool(
        name = "gitCommit",
        description = "Commits staged changes to the repository. If 'all' is true, it auto-stages all modified and deleted files.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("message") { put("type", "string"); put("description", "Commit message") }
                    putJsonObject("all") { put("type", "boolean"); put("description", "Auto-stage all modified/deleted files") }
                    putJsonObject("workspacePath") { put("type", "string"); put("description", "Path to the git repository (optional)") }
                },
                required = listOf("message"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val message = obj["message"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing message"))
            val all = obj["all"]?.asJsonPrimitive?.asBoolean ?: false
            val workspace = args.workspaceOrPrimary()
            val result = ideService.gitCommit(workspace, message, all)
            listOf(UIMessagePart.Text(result))
        },
    )

    private val gitCheckout = Tool(
        name = "gitCheckout",
        description = "Switches branches or restores working tree files.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("target") { put("type", "string"); put("description", "Branch name or commit hash to switch to") }
                    putJsonObject("createBranch") { put("type", "boolean"); put("description", "Create the branch if it doesn't exist") }
                    putJsonObject("workspacePath") { put("type", "string"); put("description", "Path to the git repository (optional)") }
                },
                required = listOf("target"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val target = obj["target"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing target"))
            val workspace = args.workspaceOrPrimary()
            val result = ideService.gitCheckout(workspace, target)
            listOf(UIMessagePart.Text(result))
        },
    )

    private val gitLog = Tool(
        name = "gitLog",
        description = "Shows commit history for the repository.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("maxCount") { put("type", "integer"); put("description", "Maximum number of commits to show (default: 10)") }
                    putJsonObject("branch") { put("type", "string"); put("description", "Branch name (default: current branch)") }
                    putJsonObject("workspacePath") { put("type", "string"); put("description", "Path to the git repository (optional)") }
                },
                required = emptyList<String>(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val maxCount = obj["maxCount"]?.asJsonPrimitive?.asInt ?: 10
            val branch = obj["branch"]?.asJsonPrimitive?.asString
            val workspace = args.workspaceOrPrimary()
            val result = ideService.runCommand("git log --oneline -$maxCount ${branch ?: ""}".trimEnd(), 30)
            listOf(UIMessagePart.Text(result.output.ifEmpty { "No commits found" }))
        },
    )

    private val gitBranch = Tool(
        name = "gitBranch",
        description = "Lists, creates, or deletes git branches.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("action") { put("type", "string"); put("description", "Action: 'list', 'create', 'delete' (default: 'list')") }
                    putJsonObject("branchName") { put("type", "string"); put("description", "Branch name (required for create/delete)") }
                    putJsonObject("workspacePath") { put("type", "string"); put("description", "Path to the git repository (optional)") }
                },
                required = emptyList<String>(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val action = obj["action"]?.asJsonPrimitive?.asString ?: "list"
            val branchName = obj["branchName"]?.asJsonPrimitive?.asString
            val workspace = args.workspaceOrPrimary()
            val result = when (action) {
                "create" -> {
                    if (branchName == null) return@Tool listOf(UIMessagePart.Text("Missing branchName for create"))
                    ideService.runCommand("git branch $branchName", 15)
                }
                "delete" -> {
                    if (branchName == null) return@Tool listOf(UIMessagePart.Text("Missing branchName for delete"))
                    ideService.runCommand("git branch -d $branchName", 15)
                }
                else -> ideService.runCommand("git branch", 15)
            }
            val text = buildString {
                if (result.output.isNotBlank()) appendLine(result.output)
                if (result.error.isNotBlank()) appendLine("STDERR: ${result.error}")
            }
            listOf(UIMessagePart.Text(text.trimEnd().ifEmpty { "Operation completed" }))
        },
    )

    private val gitPush = Tool(
        name = "gitPush",
        description = "Pushes commits to a remote repository.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("remote") { put("type", "string"); put("description", "Remote name (default: origin)") }
                    putJsonObject("branch") { put("type", "string"); put("description", "Branch name (default: current branch)") }
                    putJsonObject("setUpstream") { put("type", "boolean"); put("description", "Set upstream tracking (-u flag)") }
                    putJsonObject("workspacePath") { put("type", "string"); put("description", "Path to the git repository (optional)") }
                },
                required = emptyList<String>(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val remote = obj["remote"]?.asJsonPrimitive?.asString ?: "origin"
            val branch = obj["branch"]?.asJsonPrimitive?.asString
            val setUpstream = obj["setUpstream"]?.asJsonPrimitive?.asBoolean ?: false
            val workspace = args.workspaceOrPrimary()

            val branchFlag = if (branch != null) branch else ""
            val upstreamFlag = if (setUpstream && branch != null) "-u" else ""
            val result = ideService.runCommand("git push $upstreamFlag $remote $branchFlag".trimEnd(), 60)
            val text = buildString {
                if (result.output.isNotBlank()) appendLine(result.output)
                if (result.error.isNotBlank()) appendLine("STDERR: ${result.error}")
                appendLine("Exit code: ${result.exitCode}")
            }
            listOf(UIMessagePart.Text(text.trimEnd()))
        },
    )

    private val createPullRequest = Tool(
        name = "createPullRequest",
        description = "Creates a pull request using gh CLI. Requires gh to be installed and authenticated.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("title") { put("type", "string"); put("description", "PR title") }
                    putJsonObject("body") { put("type", "string"); put("description", "PR description/body") }
                    putJsonObject("base") { put("type", "string"); put("description", "Base branch (default: main)") }
                    putJsonObject("head") { put("type", "string"); put("description", "Head branch (default: current branch)") }
                    putJsonObject("workspacePath") { put("type", "string"); put("description", "Path to the git repository (optional)") }
                },
                required = listOf("title"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val title = obj["title"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing title"))
            val body = obj["body"]?.asJsonPrimitive?.asString?.replace("\"", "\\\"") ?: ""
            val base = obj["base"]?.asJsonPrimitive?.asString ?: "main"
            val head = obj["head"]?.asJsonPrimitive?.asString ?: ""
            val workspace = args.workspaceOrPrimary()

            val headFlag = if (head.isNotBlank()) " --head $head" else ""
            val bodyFlag = if (body.isNotBlank()) " --body \"$body\"" else ""
            val result = ideService.runCommand("gh pr create --title \"$title\"$bodyFlag --base $base$headFlag", 30)
            val text = buildString {
                if (result.output.isNotBlank()) appendLine(result.output)
                if (result.error.isNotBlank()) appendLine("STDERR: ${result.error}")
                appendLine("Exit code: ${result.exitCode}")
            }
            listOf(UIMessagePart.Text(text.trimEnd()))
        },
    )

    val all: List<Tool> = listOf(
        getGitStatus, getGitDiff, gitCommit, gitCheckout,
        gitLog, gitBranch, gitPush, createPullRequest,
    )
}
