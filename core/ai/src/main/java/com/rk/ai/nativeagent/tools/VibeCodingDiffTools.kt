package com.rk.ai.nativeagent.tools

import com.google.gson.JsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService
import java.io.File

class VibeCodingDiffTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(openDiff, getDiffResult, rejectDiff)

    private val openDiff = Tool(
        name = "openDiff",
        description = "Opens a side-by-side diff view for user review.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("filePath", "Absolute path to the file")
                    addProperty("newContent", "Proposed new content to diff against")
                },
                required = listOf("filePath", "newContent"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val filePath = obj["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            val newContent = obj["newContent"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing newContent"))
            val file = ideService.resolvePath(filePath)
            if (file == null || !file.exists()) return@Tool listOf(UIMessagePart.Text("File not found: $filePath"))
            val oldContent = ideService.getFileContent(filePath, null, null) ?: ""
            ideService.showPatch(filePath, oldContent, newContent, "Review AI file change") { }
            listOf(UIMessagePart.Text("Diff opened for $filePath"))
        },
    )

    private val getDiffResult = Tool(
        name = "getDiffResult",
        description = "Returns the current file content after a diff review.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("filePath", "Absolute path to the file")
                },
                required = listOf("filePath"),
            )
        },
        execute = { args ->
            val filePath = args.asJsonObject["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            val content = ideService.getFileContent(filePath, null, null)
            if (content != null) listOf(UIMessagePart.Text(content))
            else listOf(UIMessagePart.Text("File not found: $filePath"))
        },
    )

    private val rejectDiff = Tool(
        name = "rejectDiff",
        description = "Rejects a pending diff/patch for a file.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("filePath", "Absolute path to the file with the pending diff")
                },
                required = listOf("filePath"),
            )
        },
        execute = { args ->
            val filePath = args.asJsonObject["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            ideService.rejectPatch(filePath)
            listOf(UIMessagePart.Text("Rejected patch for $filePath"))
        },
    )
}
