package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

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
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val line = requireInt(args, "line")
        val column = requireInt(args, "column")
        val newName = requireString(args, "newName")
        ideService.renameSymbol(filePath, line, column, newName)
        return textResult("Rename operation initiated for $newName at $filePath:$line:$column")
    }
}

class FormatDocumentTool : BaseMcpTool() {
    override fun getName(): String = "formatDocument"
    override fun getDescription(): String = "Formats a document using the LSP formatter."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file to format"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        ideService.formatDocument(filePath)
        return textResult("Formatting initiated for $filePath")
    }
}