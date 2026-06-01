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

    val all: List<Tool> = listOf(getGitStatus, getGitDiff, gitCommit, gitCheckout)

    private fun com.google.gson.JsonElement.workspaceOrPrimary(): String =
        asJsonObject["workspacePath"]?.asJsonPrimitive?.asString ?: ideService.getPrimaryWorkspacePath()

    private val getGitStatus = Tool(
        name = "getGitStatus",
        description = "Returns the git status (staged, modified, untracked files) for the workspace.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("workspacePath", "Path to the git repository (optional, uses primary workspace if not set)")
                },
                required = emptyList<String>(),
            )
        },
        execute = { args ->
            val workspace = args.workspaceOrPrimary()
            val status = ideService.getGitStatus(workspace)
            val text = buildString {
                status.keySet().forEach { key ->
                    val items = status.getAsJsonArray(key)
                    if (items.size() > 0) {
                        appendLine("$key:")
                        items.forEach { appendLine("  ${it.asString}") }
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
                    put("workspacePath", "Path to the git repository (optional)")
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
                    put("message", "Commit message")
                    putJsonObject("all") { put("type", "boolean"); put("description", "Auto-stage all modified/deleted files") }
                    put("workspacePath", "Path to the git repository (optional)")
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
                    put("target", "Branch name or commit hash to switch to")
                    put("workspacePath", "Path to the git repository (optional)")
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
}
