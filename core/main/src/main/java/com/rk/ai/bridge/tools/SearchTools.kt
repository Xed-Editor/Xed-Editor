package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService

class SearchCodeTool : McpTool {
    override fun getName(): String = "searchCode"
    override fun getDescription(): String = "Searches for text patterns project-wide."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val query = args.get("query")?.asString.orEmpty()
        val limit = args.get("limit")?.asInt ?: 100
        if (query.isBlank()) throw IllegalArgumentException("query required")
        val results = ideService.searchCode(query, limit)
        return JsonObject().apply {
            add("content", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", results.toString())
                })
            })
        }
    }
}

class FindFilesTool : McpTool {
    override fun getName(): String = "findFiles"
    override fun getDescription(): String = "Finds files by name pattern project-wide."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val query = args.get("query")?.asString.orEmpty()
        val limit = args.get("limit")?.asInt ?: 100
        if (query.isBlank()) throw IllegalArgumentException("query required")
        val results = ideService.findFiles(query, limit)
        return JsonObject().apply {
            add("content", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", results.toString())
                })
            })
        }
    }
}
