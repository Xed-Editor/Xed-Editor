package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class RenameSymbolTool : BaseMcpTool() {
    override val name: String = "renameSymbol"
    override val description: String = "Renames a symbol project-wide after user review."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string", "line" to "number", "column" to "number", "newName" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val line = requireInt(args, "line")
        val column = requireInt(args, "column")
        val newName = requireString(args, "newName")
        context.ideService.renameSymbol(filePath, line, column, newName)
        return resultText("Rename operation initiated for $newName at $filePath:$line:$column")
    }
}

class FormatDocumentTool : BaseMcpTool() {
    override val name: String = "formatDocument"
    override val description: String = "Formats a document using the LSP formatter."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        context.ideService.formatDocument(filePath)
        return resultText("Formatting initiated for $filePath")
    }
}
