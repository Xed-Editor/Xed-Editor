package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class SearchCodeTool : BaseMcpTool() {
    override fun getName(): String = "searchCode"
    override fun getDescription(): String = "grep/rg equivalent - Searches for text patterns project-wide. Use this for finding code/text in files. Returns file paths and line numbers with matches."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number", "path" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val query = requireString(args, "query")
        val limit = optionalInt(args, "limit") ?: 50
        val results = ideService.searchCode(query, limit)
        return jsonResult(JsonObject().apply { add("results", results) })
    }
}

class GrepTool : BaseMcpTool() {
    override fun getName(): String = "grep"
    override fun getDescription(): String = "ALIAS for searchCode - Searches for text patterns project-wide. Use this for finding code/text in files. Returns file paths and line numbers with matches."
    override fun getRequiredParams(): Map<String, String> = mapOf("pattern" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number", "path" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val pattern = requireString(args, "pattern")
        val limit = optionalInt(args, "limit") ?: 50
        val results = ideService.searchCode(pattern, limit)
        return jsonResult(JsonObject().apply { add("results", results) })
    }
}

class SearchSymbolsTool : BaseMcpTool() {
    override fun getName(): String = "searchSymbols"
    override fun getDescription(): String = "RECOMMENDED: ctags/zsymbol equivalent - Searches for code declarations (classes, functions, variables) project-wide. Much faster and more precise than searchCode for finding where a symbol is defined."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val query = requireString(args, "query")
        val limit = optionalInt(args, "limit") ?: 50
        val results = ideService.searchCode(query, limit)
        return jsonResult(JsonObject().apply { add("symbols", results) })
    }
}


class FindFilesTool : BaseMcpTool() {
    override fun getName(): String = "findFiles"
    override fun getDescription(): String = "find equivalent - Finds files by name pattern project-wide. Use glob patterns like '*.kt' or '**/*.java'"
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val query = requireString(args, "query")
        val limit = optionalInt(args, "limit") ?: 100
        val results = ideService.findFiles(query, limit)
        return textResult(results.toString())
    }
}

class GlobTool : BaseMcpTool() {
    override fun getName(): String = "glob"
    override fun getDescription(): String = "ALIAS for findFiles - Finds files by name pattern project-wide. Use glob patterns like '*.kt' or '**/*.java'"
    override fun getRequiredParams(): Map<String, String> = mapOf("pattern" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val pattern = requireString(args, "pattern")
        val limit = optionalInt(args, "limit") ?: 100
        val results = ideService.findFiles(pattern, limit)
        return textResult(results.toString())
    }
}
