@file:OptIn(ExperimentalUuidApi::class)

package com.rk.ai.agent.tools

import com.google.gson.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService

class VibeCodingSearchTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(searchCode, grepSearch, searchSymbols)

    private val searchCode = Tool(
        name = "searchCode",
        description = "Search for text in the project. Supports plain text and regex. Returns file paths with line numbers.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", "Text or regex to search for")
                    putJsonObject("limit") { put("type", "integer"); put("description", "Maximum results (default: 50)") }
                    putJsonObject("isRegex") { put("type", "boolean"); put("description", "Use regex if true") }
                    put("path", "Directory to scope search to (optional)")
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
                properties = buildJsonObject {
                    put("pattern", "Regex pattern to search for")
                    putJsonObject("limit") { put("type", "integer") }
                    put("path", "Directory to scope search to (optional)")
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

    private val searchSymbols = Tool(
        name = "searchSymbols",
        description = "PREFERRED for code declarations — searches classes, functions, variables. Faster and more precise than grep.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", "Symbol name to search for")
                    putJsonObject("limit") { put("type", "integer"); put("description", "Maximum results (default: 50)") }
                    put("path", "Directory to scope search to (optional)")
                },
                required = listOf("query"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val query = obj["query"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing query"))
            val limit = obj["limit"]?.asJsonPrimitive?.asInt ?: 50
            val path = obj["path"]?.asJsonPrimitive?.asString
            val results = ideService.searchSymbols(query, limit, path)
            if (results.size() > 0) {
                val text = results.joinToString("\n") { "${it.asJsonObject["path"]?.asString ?: "?"}:${it.asJsonObject["line"]?.asInt ?: 0}" }
                listOf(UIMessagePart.Text(text))
            } else {
                listOf(UIMessagePart.Text("No symbols found for: $query"))
            }
        },
    )

    private val findFiles = Tool(
        name = "findFiles",
        description = "Finds files by name patterns using glob matching.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("pattern", "File name or glob pattern (e.g. *.kt, **/*.java)")
                    putJsonObject("limit") { put("type", "integer"); put("description", "Maximum results (default: 100)") }
                    put("path", "Directory to search in (optional)")
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
