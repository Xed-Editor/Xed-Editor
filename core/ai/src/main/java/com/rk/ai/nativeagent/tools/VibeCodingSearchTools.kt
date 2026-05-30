package com.rk.ai.nativeagent.tools

import com.google.gson.JsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService

class VibeCodingSearchTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(searchCode, grepSearch, findFiles)

    private val searchCode = Tool(
        name = "searchCode",
        description = "Search for text in the project. Supports plain text and regex. Returns file paths with line numbers.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("query", "Text or regex to search for")
                    add("limit", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Maximum results") })
                    add("isRegex", JsonObject().apply { addProperty("type", "boolean"); addProperty("description", "Use regex if true") })
                    addProperty("path", "Directory to scope search to (optional)")
                },
                required = listOf("query"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val query = obj["query"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing query"))
            val limit = obj["limit"]?.asJsonPrimitive?.asInt ?: 50
            val isRegex = obj["isRegex"]?.asJsonPrimitive?.asBoolean ?: false
            val path = obj["path"]?.asJsonPrimitive?.asString
            val results = ideService.searchCode(query, limit, path, isRegex)
            if (results.size() > 0) {
                val text = results.joinToString("\n") { "${it.asJsonObject["path"]?.asString ?: "?"}:${it.asJsonObject["line"]?.asInt ?: 0}" }
                listOf(UIMessagePart.Text(text))
            } else {
                listOf(UIMessagePart.Text("No results found for: $query"))
            }
        },
    )

    private val grepSearch = Tool(
        name = "grep",
        description = "Alias for searchCode with regex support. Searches file contents project-wide.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("pattern", "Regex pattern to search for")
                    add("limit", JsonObject().apply { addProperty("type", "integer") })
                    addProperty("path", "Directory to scope search to (optional)")
                },
                required = listOf("pattern"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val pattern = obj["pattern"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing pattern"))
            val limit = obj["limit"]?.asJsonPrimitive?.asInt ?: 50
            val path = obj["path"]?.asJsonPrimitive?.asString
            val results = ideService.searchCode(pattern, limit, path, isRegex = true)
            if (results.size() > 0) {
                val text = results.joinToString("\n") { "${it.asJsonObject["path"]?.asString ?: "?"}:${it.asJsonObject["line"]?.asInt ?: 0} ${it.asJsonObject["text"]?.asString ?: ""}" }
                listOf(UIMessagePart.Text(text))
            } else {
                listOf(UIMessagePart.Text("No matches found for: $pattern"))
            }
        },
    )

    private val findFiles = Tool(
        name = "findFiles",
        description = "Find files by name patterns using glob matching.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("pattern", "File name or glob pattern (e.g. *.kt, **/*.java)")
                    add("limit", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Maximum results") })
                    addProperty("path", "Directory to search in (optional)")
                },
                required = listOf("pattern"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val pattern = obj["pattern"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing pattern"))
            val limit = obj["limit"]?.asJsonPrimitive?.asInt ?: 100
            val path = obj["path"]?.asJsonPrimitive?.asString
            val results = ideService.findFiles(pattern, limit, path)
            if (results.size() > 0) {
                val text = results.joinToString("\n") { it.asString }
                listOf(UIMessagePart.Text(text))
            } else {
                listOf(UIMessagePart.Text("No files found matching: $pattern"))
            }
        },
    )
}
