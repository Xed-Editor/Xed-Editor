package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class RenameSymbolTool : BaseMcpTool() {
    override fun getName(): String = "renameSymbol"
    override fun getDescription(): String = "Renames a symbol project-wide after user review."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "line" to "number", "column" to "number", "newName" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file containing the symbol",
        "line" to "Line number of the symbol (1-indexed)",
        "column" to "Column number of the symbol (1-indexed)",
        "newName" to "New name for the symbol"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val line = requireInt(args, "line")
        val column = requireInt(args, "column")
        val newName = requireString(args, "newName")
        context.ideService.renameSymbol(filePath, line, column, newName)
        return McpToolResult.success("Rename operation initiated for $newName at $filePath:$line:$column")
    }
}

class FormatDocumentTool : BaseMcpTool() {
    override fun getName(): String = "formatDocument"
    override fun getDescription(): String = "Formats a document using the LSP formatter."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file to format"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        context.ideService.formatDocument(filePath)
        return McpToolResult.success("Formatting initiated for $filePath")
    }
}
