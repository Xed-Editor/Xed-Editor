package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class GetDiagnosticsTool : BaseMcpTool() {
    override val name: String = "getDiagnostics"
    override val description: String = "Returns LSP diagnostics (errors, warnings) for a file. Use this to verify the correctness of your changes. Note: IDE automatically pushes new diagnostics after a write."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val results = context.ideService.getDiagnostics(filePath)
        return resultText(results.toString())
    }
}

class FindDefinitionsTool : BaseMcpTool() {
    override val name: String = "findDefinitions"
    override val description: String = "Finds the definition of a symbol at the given cursor position. Use this for precise code navigation instead of searching for text."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string", "line" to "number", "column" to "number")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val line = requireInt(args, "line")
        val column = requireInt(args, "column")
        val results = context.ideService.findDefinitions(filePath, line, column)
        return resultText(results.toString())
    }
}

class FindReferencesTool : BaseMcpTool() {
    override val name: String = "findReferences"
    override val description: String = "Finds all usages of a symbol at the given cursor position. Use this to understand the impact of changing a symbol or function signature."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string", "line" to "number", "column" to "number")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val line = requireInt(args, "line")
        val column = requireInt(args, "column")
        val results = context.ideService.findReferences(filePath, line, column)
        return resultText(results.toString())
    }
}
