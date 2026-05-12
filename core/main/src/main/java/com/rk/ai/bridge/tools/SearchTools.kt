package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class SearchCodeTool : BaseMcpTool() {
    override fun getName(): String = "searchCode"
    override fun getDescription(): String = "Searches for text patterns project-wide."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val query = requireString(args, "query")
        val limit = optionalInt(args, "limit", 100)
        val results = ideService.searchCode(query, limit)
        return textResult(results.toString())
    }
}

class FindFilesTool : BaseMcpTool() {
    override fun getName(): String = "findFiles"
    override fun getDescription(): String = "Finds files by name pattern project-wide."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val query = requireString(args, "query")
        val limit = optionalInt(args, "limit", 100)
        val results = ideService.findFiles(query, limit)
        return textResult(results.toString())
    }
}
