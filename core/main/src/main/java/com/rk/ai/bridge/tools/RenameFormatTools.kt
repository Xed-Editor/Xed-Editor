package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService

class RenameSymbolTool : McpTool {
    override fun getName(): String = "renameSymbol"
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val line = args.get("line")?.asInt ?: throw IllegalArgumentException("line required")
        val column = args.get("column")?.asInt ?: throw IllegalArgumentException("column required")
        val newName = args.get("newName")?.asString.orEmpty()
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        if (newName.isBlank()) throw IllegalArgumentException("newName required")
        
        ideService.renameSymbol(filePath, line, column, newName)
        return textResult("Rename operation initiated for $newName at $filePath:$line:$column")
    }
}

class FormatDocumentTool : McpTool {
    override fun getName(): String = "formatDocument"
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        
        ideService.formatDocument(filePath)
        return textResult("Formatting initiated for $filePath")
    }
}
