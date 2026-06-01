@file:OptIn(ExperimentalUuidApi::class)

package com.rk.ai.agent.tools

import com.google.gson.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService
import java.io.File

class VibeCodingEditorTools(private val ideService: IdeService) {

    private val getOpenFiles = Tool(
        name = "getOpenFiles",
        description = "Lists all files currently open in editor tabs.",
        execute = { _ ->
            val files = ideService.getOpenFiles()
            val text = files.joinToString("\n") { it["path"]?.asString ?: it.toString() }
            listOf(UIMessagePart.Text(text.ifEmpty { "No files open" }))
        },
    )

    private val getActiveFile = Tool(
        name = "getActiveFile",
        description = "Returns the path and full content of the file currently visible in the active editor tab. Content is truncated at 500KB.",
        execute = { _ ->
            val json = ideService.getActiveFile()
            if (json != null) {
                val path = json["path"]?.asString ?: "unknown"
                val content = json["content"]?.asString ?: ""
                listOf(UIMessagePart.Text("File: $path\n\n$content"))
            } else {
                listOf(UIMessagePart.Text("No active file open"))
            }
        },
    )

    private val getSelection = Tool(
        name = "getSelection",
        description = "Returns the text currently selected by the user in the active editor.",
        execute = { _ ->
            val selection = ideService.getSelection()
            listOf(UIMessagePart.Text(selection.ifEmpty { "No text selected" }))
        },
    )

    private val openFile = Tool(
        name = "openFile",
        description = "Opens a file in an editor tab.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("filePath") { put("type", "string"); put("description", "Absolute path to the file to open") }
                },
                required = listOf("filePath"),
            )
        },
        execute = { args ->
            val filePath = args.asJsonObject["filePath"]?.asJsonPrimitive?.asString
            if (filePath != null) {
                ideService.openFile(File(filePath))
                listOf(UIMessagePart.Text("Opened $filePath"))
            } else {
                listOf(UIMessagePart.Text("Missing filePath"))
            }
        },
    )

    private val replaceSelection = Tool(
        name = "replaceSelection",
        description = "Replaces the user's current selection with new text. Opens a review tab for the user.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("newContent") { put("type", "string"); put("description", "Text to replace the selection with") }
                },
                required = listOf("newContent"),
            )
        },
        execute = { args ->
            val newContent = args.asJsonObject["newContent"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing newContent"))
            ideService.replaceSelection(newContent)
            listOf(UIMessagePart.Text("Selection replaced"))
        },
    )

    private val insertAtCursor = Tool(
        name = "insertAtCursor",
        description = "Inserts text at the user's current cursor position. Opens a review tab for the user.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("newContent") { put("type", "string"); put("description", "Text to insert at the cursor") }
                },
                required = listOf("newContent"),
            )
        },
        execute = { args ->
            val newContent = args.asJsonObject["newContent"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing newContent"))
            ideService.insertAtCursor(newContent)
            listOf(UIMessagePart.Text("Inserted at cursor"))
        },
    )

    private val saveOpenFiles = Tool(
        name = "saveOpenFiles",
        description = "Saves all unsaved changes in all open editor tabs. Recommended before running external commands.",
        execute = { _ ->
            val result = ideService.saveAllFiles()
            listOf(UIMessagePart.Text(result.ifEmpty { "All files saved" }))
        },
    )

    private val refreshOpenEditors = Tool(
        name = "refreshOpenEditors",
        description = "Refreshes all non-dirty open editor tabs from disk.",
        execute = { _ ->
            ideService.refreshEditors(null, false)
            listOf(UIMessagePart.Text("Open editors refreshed"))
        },
    )

    private val refreshFile = Tool(
        name = "refreshFile",
        description = "Refreshes a specific editor tab from disk.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("filePath") { put("type", "string"); put("description", "Absolute path of the file to refresh") }
                },
                required = listOf("filePath"),
            )
        },
        execute = { args ->
            val filePath = args.asJsonObject["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            ideService.refreshEditors(filePath, false)
            listOf(UIMessagePart.Text("Refreshed $filePath"))
        },
    )

    val all: List<Tool> = listOf(
        getOpenFiles, getActiveFile, getSelection,
        openFile, replaceSelection, insertAtCursor,
        saveOpenFiles, refreshOpenEditors, refreshFile,
    )
}
