package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.GeminiIdeService

class GetDiagnosticsTool : McpTool {
    override fun getName(): String = "getDiagnostics"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        return jsonResult(ideService.getDiagnostics(filePath))
    }
}

class FindDefinitionsTool : McpTool {
    override fun getName(): String = "findDefinitions"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val line = args.get("line")?.asInt ?: throw IllegalArgumentException("line required")
        val column = args.get("column")?.asInt ?: throw IllegalArgumentException("column required")
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        return jsonResult(ideService.findDefinitions(filePath, line, column))
    }
}

class FindReferencesTool : McpTool {
    override fun getName(): String = "findReferences"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val line = args.get("line")?.asInt ?: throw IllegalArgumentException("line required")
        val column = args.get("column")?.asInt ?: throw IllegalArgumentException("column required")
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        return jsonResult(ideService.findReferences(filePath, line, column))
    }
}
