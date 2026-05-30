package com.rk.ai.nativeagent.tools

import com.google.gson.JsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService

class VibeCodingSystemTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(getIdeInfo, showMessage)

    private val getIdeInfo = Tool(
        name = "getIdeInfo",
        description = "Returns IDE name, version, and current workspace path.",
        execute = { _ ->
            val openFiles = ideService.getOpenFiles()
            val workspace = ideService.getPrimaryWorkspacePath()
            val text = buildString {
                appendLine("IDE: Xed-Editor")
                appendLine("Workspace: $workspace")
                appendLine("Open files: ${openFiles.size}")
                openFiles.forEach { appendLine("  - ${it["path"]?.asString ?: "?"}") }
            }
            listOf(UIMessagePart.Text(text))
        },
    )

    private val showMessage = Tool(
        name = "showMessage",
        description = "Displays a short toast notification message to the user.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("message", "Message text to display")
                },
                required = listOf("message"),
            )
        },
        execute = { args ->
            val message = args.asJsonObject["message"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text(""))
            ideService.showMessage(message)
            listOf(UIMessagePart.Text(""))
        },
    )
}
