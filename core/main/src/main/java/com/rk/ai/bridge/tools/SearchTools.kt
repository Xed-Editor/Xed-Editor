package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.GeminiIdeService

class SearchCodeTool : McpTool {
    override fun getName(): String = "searchCode"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val query = args.get("query")?.asString.orEmpty()
        val limit = args.get("limit")?.asInt ?: 100
        if (query.isBlank()) throw IllegalArgumentException("query required")
        val results = ideService.searchCode(query, limit)
        return jsonResult(results)
    }
}

class FindFilesTool : McpTool {
    override fun getName(): String = "findFiles"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val query = args.get("query")?.asString.orEmpty()
        val limit = args.get("limit")?.asInt ?: 100
        if (query.isBlank()) throw IllegalArgumentException("query required")
        val results = ideService.findFiles(query, limit)
        return jsonResult(results)
    }
}
