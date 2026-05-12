package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GetDiagnosticsTool : BaseMcpTool() {
    override fun getName(): String = "getDiagnostics"
    override fun getDescription(): String = "Returns LSP diagnostics for a file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val results = ideService.getDiagnostics(filePath)
        return textResult(results.toString())
    }
}

class FindDefinitionsTool : BaseMcpTool() {
    override fun getName(): String = "findDefinitions"
    override fun getDescription(): String = "Finds the definition location of a symbol."
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
    override fun getDescription(): String = "Finds all references to a symbol."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "line" to "number", "column" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val line = requireInt(args, "line")
        val column = requireInt(args, "column")
        val results = ideService.findReferences(filePath, line, column)
        return textResult(results.toString())
    }
}
