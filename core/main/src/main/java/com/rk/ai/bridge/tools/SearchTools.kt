package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class SearchCodeTool : BaseMcpTool() {
    override val name: String = "searchCode"
    override val description: String = "Searches for text patterns project-wide."
    override val requiredParams: Map<String, String> = mapOf("query" to "string")
    override val optionalParams: Map<String, String> = mapOf("limit" to "number")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = requireString(args, "query")
        val limit = optionalInt(args, "limit", 50)
        val results = context.ideService.searchCode(query, limit)
        return resultJson(results)
    }
}

class SearchSymbolsTool : BaseMcpTool() {
    override val name: String = "searchSymbols"
    override val description: String = "RECOMMENDED: Searches for code declarations (classes, functions, variables) project-wide. Much faster and more precise than searchCode for finding where a symbol is defined."
    override val requiredParams: Map<String, String> = mapOf("query" to "string")
    override val optionalParams: Map<String, String> = mapOf("limit" to "number")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = requireString(args, "query")
        val limit = optionalInt(args, "limit", 50)
        val results = context.ideService.searchCode(query, limit)
        return resultJson(JsonObject().apply { add("symbols", results) })
    }
}

class FindFilesTool : BaseMcpTool() {
    override val name: String = "findFiles"
    override val description: String = "Finds files by name pattern project-wide."
    override val requiredParams: Map<String, String> = mapOf("query" to "string")
    override val optionalParams: Map<String, String> = mapOf("limit" to "number")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = requireString(args, "query")
        val limit = optionalInt(args, "limit", 100)
        val results = context.ideService.findFiles(query, limit)
        return resultText(results.toString())
    }
}
