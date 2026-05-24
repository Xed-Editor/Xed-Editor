package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
class SearchCodeTool : BaseMcpTool() {
    override fun getName(): String = "searchCode"
    override fun getDescription(): String = "Searches text patterns project-wide. Accepts: query, pattern, search, text."
    override fun getOptionalParams(): Map<String, String> = mapOf("query" to "string", "pattern" to "string", "search" to "string", "text" to "string", "limit" to "number", "path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "query" to "Text to search for",
        "pattern" to "Alternative to query",
        "search" to "Alternative to query",
        "text" to "Alternative to query",
        "limit" to "Maximum results to return (default: 50)",
        "path" to "Scope search to a specific directory"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = getQueryParam(args) ?: throw ToolError.MissingParam("query/pattern/search/text")
        val limit = optionalPositiveInt(args, "limit") ?: 50
        val path = getPathParam(args)
        val results = context.ideService.searchCode(query, limit, path = path, isRegex = false)
        return McpToolResult.success(results.toString())
    }
}

class GrepTool : BaseMcpTool() {
    override fun getName(): String = "grep"
    override fun getDescription(): String = "Alias for searchCode with regex support. Searches text patterns project-wide."
    override fun getOptionalParams(): Map<String, String> = mapOf("query" to "string", "pattern" to "string", "search" to "string", "text" to "string", "limit" to "number", "path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "query" to "Regex pattern to search for",
        "pattern" to "Alternative to query",
        "search" to "Alternative to query",
        "text" to "Alternative to query",
        "limit" to "Maximum results to return (default: 50)",
        "path" to "Scope search to a specific directory"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = getQueryParam(args) ?: throw ToolError.MissingParam("query/pattern/search/text")
        val limit = optionalPositiveInt(args, "limit") ?: 50
        val path = getPathParam(args)
        val results = context.ideService.searchCode(query, limit, path = path, isRegex = true)
        return McpToolResult.success(results.toString())
    }
}

class SearchSymbolsTool : BaseMcpTool() {
    override fun getName(): String = "searchSymbols"
    override fun getDescription(): String = "Searches code declarations (classes, functions, variables). Faster and more precise than grep."
    override fun getOptionalParams(): Map<String, String> = mapOf("query" to "string", "pattern" to "string", "symbol" to "string", "limit" to "number", "path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "query" to "Symbol name to search for",
        "pattern" to "Alternative to query",
        "symbol" to "Alternative to query",
        "limit" to "Maximum results to return (default: 50)",
        "path" to "Scope search to a specific directory"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = getQueryParam(args) ?: throw ToolError.MissingParam("query/pattern/symbol")
        val limit = optionalPositiveInt(args, "limit") ?: 50
        val path = getPathParam(args)
        val results = context.ideService.searchSymbols(query, limit, path = path)
        return McpToolResult.success(results.toString())
    }
}

class FindFilesTool : BaseMcpTool() {
    override fun getName(): String = "findFiles"
    override fun getDescription(): String = "Finds files by glob patterns like '*.kt' or '**/*.java'. Accepts: query, pattern, limit, path."
    override fun getOptionalParams(): Map<String, String> = mapOf("query" to "string", "pattern" to "string", "limit" to "number", "path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "query" to "File name or glob pattern to search for (e.g. *.kt, **/*.java)",
        "pattern" to "Alternative to query",
        "limit" to "Maximum results to return (default: 100)",
        "path" to "Directory to search in (default: workspace root)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = getQueryParam(args) ?: throw ToolError.MissingParam("query/pattern")
        val limit = optionalPositiveInt(args, "limit") ?: 100
        val path = getPathParam(args)
        val results = context.ideService.findFiles(query, limit, path)
        return McpToolResult.success(results.toString())
    }
}

class GlobTool : BaseMcpTool() {
    override fun getName(): String = "glob"
    override fun getDescription(): String = "Alias for findFiles. Finds files by glob patterns."
    override fun getOptionalParams(): Map<String, String> = mapOf("query" to "string", "pattern" to "string", "limit" to "number", "path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "query" to "File name or glob pattern to search for (e.g. *.kt, **/*.java)",
        "pattern" to "Alternative to query",
        "limit" to "Maximum results to return (default: 100)",
        "path" to "Directory to search in (default: workspace root)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = getQueryParam(args) ?: throw ToolError.MissingParam("query/pattern")
        val limit = optionalPositiveInt(args, "limit") ?: 100
        val path = getPathParam(args)
        val results = context.ideService.findFiles(query, limit, path)
        return McpToolResult.success(results.toString())
    }
}
