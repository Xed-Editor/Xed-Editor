package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class GetDiagnosticsTool : BaseMcpTool() {
    override fun getName(): String = "getDiagnostics"
    override fun getDescription(): String = "Returns LSP diagnostics (errors, warnings) for a file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val results = context.ideService.getDiagnostics(filePath)
        return McpToolResult.success(results.toString())
    }
}

class FindDefinitionsTool : BaseMcpTool() {
    override fun getName(): String = "findDefinitions"
    override fun getDescription(): String = "Finds the definition of a symbol at the given cursor position."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "line" to "number", "column" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file",
        "line" to "Line number (1-indexed)",
        "column" to "Column number (1-indexed)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val line = requireInt(args, "line")
        val column = requireInt(args, "column")
        val results = context.ideService.findDefinitions(filePath, line, column)
        return McpToolResult.success(results.toString())
    }
}

class FindReferencesTool : BaseMcpTool() {
    override fun getName(): String = "findReferences"
    override fun getDescription(): String = "Finds all usages of a symbol at the given cursor position."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "line" to "number", "column" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file",
        "line" to "Line number (1-indexed)",
        "column" to "Column number (1-indexed)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val line = requireInt(args, "line")
        val column = requireInt(args, "column")
        val results = context.ideService.findReferences(filePath, line, column)
        return McpToolResult.success(results.toString())
    }
}
