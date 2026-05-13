package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GetDiagnosticsTool : BaseMcpTool() {
    override fun getName(): String = "getDiagnostics"
    override fun getDescription(): String = "Returns LSP diagnostics (errors, warnings) for a file. Use this to verify the correctness of your changes. Note: IDE automatically pushes new diagnostics after a write."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val results = ideService.getDiagnostics(filePath)
        return textResult(results.toString())
    }
}

class FindDefinitionsTool : BaseMcpTool() {
    override fun getName(): String = "findDefinitions"
    override fun getDescription(): String = "Finds the definition of a symbol at the given cursor position. Use this for precise code navigation instead of searching for text."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "line" to "number", "column" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val line = requireInt(args, "line")
        val column = requireInt(args, "column")
        val results = ideService.findDefinitions(filePath, line, column)
        return textResult(results.toString())
    }
}

class FindReferencesTool : BaseMcpTool() {
    override fun getName(): String = "findReferences"
    override fun getDescription(): String = "Finds all usages of a symbol at the given cursor position. Use this to understand the impact of changing a symbol or function signature."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "line" to "number", "column" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val line = requireInt(args, "line")
        val column = requireInt(args, "column")
        val results = ideService.findReferences(filePath, line, column)
        return textResult(results.toString())
    }
}
