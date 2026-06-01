package com.rk.ai.agent.tools

import com.google.gson.JsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService

class VibeCodingLspTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(getDiagnostics, findDefinitions, findReferences, formatDocument)

    private val getDiagnostics = Tool(
        name = "getDiagnostics",
        description = "Returns LSP diagnostics (errors, warnings, hints) for a file.",
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
            val diagnostics = ideService.getDiagnostics(filePath)
            if (diagnostics.size() > 0) {
                val text = diagnostics.joinToString("\n") { diag ->
                    val d = diag.asJsonObject
                    val line = d["line"]?.asInt ?: 0
                    val col = d["column"]?.asInt ?: 0
                    val message = d["message"]?.asString ?: ""
                    val severity = d["severity"]?.asString ?: "info"
                    "[$severity] $line:$col - $message"
                }
                listOf(UIMessagePart.Text(text))
            } else {
                listOf(UIMessagePart.Text("No diagnostics for $filePath"))
            }
        },
    )

    private val findDefinitions = Tool(
        name = "findDefinitions",
        description = "Finds the definition of a symbol at the given cursor position in a file.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("filePath", "Absolute path to the file")
                    add("line", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Line number (1-indexed)") })
                    add("column", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Column number (1-indexed)") })
                },
                required = listOf("filePath", "line", "column"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val filePath = obj["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            val line = obj["line"]?.asJsonPrimitive?.asInt ?: return@Tool listOf(UIMessagePart.Text("Missing line"))
            val column = obj["column"]?.asJsonPrimitive?.asInt ?: return@Tool listOf(UIMessagePart.Text("Missing column"))
            val definitions = ideService.findDefinitions(filePath, line, column)
            if (definitions.size() > 0) {
                val text = definitions.joinToString("\n") { it.asJsonObject["path"]?.asString ?: it.toString() }
                listOf(UIMessagePart.Text(text))
            } else {
                listOf(UIMessagePart.Text("No definition found"))
            }
        },
    )

    private val findReferences = Tool(
        name = "findReferences",
        description = "Finds all references to a symbol at the given cursor position.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("filePath", "Absolute path to the file")
                    add("line", JsonObject().apply { addProperty("type", "integer") })
                    add("column", JsonObject().apply { addProperty("type", "integer") })
                },
                required = listOf("filePath", "line", "column"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val filePath = obj["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            val line = obj["line"]?.asJsonPrimitive?.asInt ?: return@Tool listOf(UIMessagePart.Text("Missing line"))
            val column = obj["column"]?.asJsonPrimitive?.asInt ?: return@Tool listOf(UIMessagePart.Text("Missing column"))
            val references = ideService.findReferences(filePath, line, column)
            if (references.size() > 0) {
                val text = references.joinToString("\n") { it.asJsonObject["path"]?.asString ?: it.toString() }
                listOf(UIMessagePart.Text(text))
            } else {
                listOf(UIMessagePart.Text("No references found"))
            }
        },
    )

    private val formatDocument = Tool(
        name = "formatDocument",
        description = "Formats a document using the LSP formatter.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("filePath", "Absolute path to the file to format")
                },
                required = listOf("filePath"),
            )
        },
        execute = { args ->
            val filePath = args.asJsonObject["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            ideService.formatDocument(filePath)
            listOf(UIMessagePart.Text("Formatted $filePath"))
        },
    )
}
