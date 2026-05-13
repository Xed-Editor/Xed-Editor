package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class SearchCodeTool : BaseMcpTool() {
    override fun getName(): String = "searchCode"
    override fun getDescription(): String = "NATIVE text search - DO NOT use runCommand('grep ...'). Searches text patterns project-wide with pre-built index. Much faster than terminal grep. Accepts: query, pattern, search, text."
    override fun getRequiredParams(): Map<String, String> = emptyMap()
    override fun getOptionalParams(): Map<String, String> = mapOf("query" to "string", "pattern" to "string", "search" to "string", "text" to "string", "limit" to "number", "path" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val query = getQueryParam(args) ?: throw ToolError.MissingParam("query/pattern/search/text")
        val limit = optionalPositiveInt(args, "limit") ?: 50
        val path = getPathParam(args)
        val results = ideService.searchCode(query, limit, path = path, isRegex = false)
        return jsonResult(JsonObject().apply { add("results", results) })
    }
}

class GrepTool : BaseMcpTool() {
    override fun getName(): String = "grep"
    override fun getDescription(): String = "NATIVE grep - DO NOT use runCommand('grep ...'). Alias for searchCode. Searches text patterns project-wide. Accepts: query, pattern, search, text."
    override fun getRequiredParams(): Map<String, String> = emptyMap()
    override fun getOptionalParams(): Map<String, String> = mapOf("query" to "string", "pattern" to "string", "search" to "string", "text" to "string", "limit" to "number", "path" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val query = getQueryParam(args) ?: throw ToolError.MissingParam("query/pattern/search/text")
        val limit = optionalPositiveInt(args, "limit") ?: 50
        val path = getPathParam(args)
        val results = ideService.searchCode(query, limit, path = path, isRegex = true)
        return jsonResult(JsonObject().apply { add("results", results) })
    }
}

class SearchSymbolsTool : BaseMcpTool() {
    override fun getName(): String = "searchSymbols"
    override fun getDescription(): String = "NATIVE symbol search - DO NOT use runCommand('grep class ...'). Searches code declarations (classes, functions, variables). Faster and more precise than grep. Accepts: query, pattern, symbol."
    override fun getRequiredParams(): Map<String, String> = emptyMap()
    override fun getOptionalParams(): Map<String, String> = mapOf("query" to "string", "pattern" to "string", "symbol" to "string", "limit" to "number", "path" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val query = getQueryParam(args) ?: throw ToolError.MissingParam("query/pattern/symbol")
        val limit = optionalPositiveInt(args, "limit") ?: 50
        val path = getPathParam(args)
        val results = ideService.searchSymbols(query, limit, path = path)
        return jsonResult(JsonObject().apply { add("symbols", results) })
    }
}


class FindFilesTool : BaseMcpTool() {
    override fun getName(): String = "findFiles"
    override fun getDescription(): String = "NATIVE file finder - DO NOT use runCommand('find ...'). Finds files by glob patterns like '*.kt' or '**/*.java'. Much faster than terminal find. Accepts: query, pattern."
    override fun getRequiredParams(): Map<String, String> = emptyMap()
    override fun getOptionalParams(): Map<String, String> = mapOf("query" to "string", "pattern" to "string", "limit" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val query = getQueryParam(args) ?: throw ToolError.MissingParam("query/pattern")
        val limit = optionalPositiveInt(args, "limit") ?: 100
        val results = ideService.findFiles(query, limit)
        return textResult(results.toString())
    }
}

class GlobTool : BaseMcpTool() {
    override fun getName(): String = "glob"
    override fun getDescription(): String = "NATIVE glob finder - DO NOT use runCommand('find ...'). Alias for findFiles. Finds files by glob patterns. Accepts: query, pattern."
    override fun getRequiredParams(): Map<String, String> = emptyMap()
    override fun getOptionalParams(): Map<String, String> = mapOf("query" to "string", "pattern" to "string", "limit" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val query = getQueryParam(args) ?: throw ToolError.MissingParam("query/pattern")
        val limit = optionalPositiveInt(args, "limit") ?: 100
        val results = ideService.findFiles(query, limit)
        return textResult(results.toString())
    }
}
