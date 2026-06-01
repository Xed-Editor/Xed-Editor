@file:OptIn(ExperimentalUuidApi::class)

package com.rk.ai.agent.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class VibeCodingPackageTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(npmSearch, pipSearch, mavenSearch)

    private val npmSearch = Tool(
        name = "npm_search",
        description = "Searches npm registry for packages.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", "Package name or search term")
                    putJsonObject("limit") { put("type", "integer"); put("description", "Maximum results (default: 10)") }
                },
                required = listOf("query"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val query = obj["query"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing query"))
            val limit = (obj["limit"]?.asJsonPrimitive?.asInt ?: 10).coerceIn(1, 50)

            val url = "https://registry.npmjs.org/-/v1/search?text=${URLEncoder.encode(query, "UTF-8")}&size=$limit"
            val json = httpGet(url)
            val data = JsonParser.parseString(json).asJsonObject
            val objects = data.getAsJsonArray("objects") ?: JsonArray()

            val text = buildString {
                appendLine("npm search results for: $query")
                appendLine()
                objects.forEach { objEntry ->
                    val pkg = objEntry.asJsonObject?.getAsJsonObject("package") ?: return@forEach
                    val name = pkg.get("name")?.asString ?: "?"
                    val version = pkg.get("version")?.asString ?: "?"
                    val description = pkg.get("description")?.asString ?: ""
                    val publisher = pkg.getAsJsonObject("publisher")?.get("username")?.asString
                        ?: pkg.getAsJsonObject("author")?.get("name")?.asString ?: "?"
                    appendLine("$name@$version")
                    if (description.isNotBlank()) appendLine("  $description")
                    appendLine("  Publisher: $publisher")
                    appendLine()
                }
            }
            listOf(UIMessagePart.Text(text.ifEmpty { "No packages found for: $query" }))
        },
    )

    private val pipSearch = Tool(
        name = "pip_search",
        description = "Searches PyPI (Python Package Index) for packages.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", "Package name or search term")
                    putJsonObject("limit") { put("type", "integer"); put("description", "Maximum results (default: 10)") }
                },
                required = listOf("query"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val query = obj["query"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing query"))
            val limit = (obj["limit"]?.asJsonPrimitive?.asInt ?: 10).coerceIn(1, 50)

            val html = httpGet("https://pypi.org/simple/")
            val lines = html.split("\n")
                .filter { it.contains(query, ignoreCase = true) }
                .map { it.replace(Regex("<[^>]*>"), "").trim() }
                .filter { it.isNotBlank() }
                .take(limit)

            val text = buildString {
                appendLine("PyPI packages matching: $query")
                appendLine()
                lines.forEach { appendLine("  $it") }
            }
            listOf(UIMessagePart.Text(text.ifEmpty { "No packages found for: $query" }))
        },
    )

    private val mavenSearch = Tool(
        name = "maven_search",
        description = "Searches Maven Central for artifacts.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", "Search term (groupId:artifactId or name)")
                    putJsonObject("limit") { put("type", "integer"); put("description", "Maximum results (default: 10)") }
                },
                required = listOf("query"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val query = obj["query"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing query"))
            val limit = (obj["limit"]?.asJsonPrimitive?.asInt ?: 10).coerceIn(1, 50)

            val url = "https://search.maven.org/solrsearch/select?q=${URLEncoder.encode(query, "UTF-8")}&rows=$limit&wt=json"
            val json = httpGet(url)
            val data = JsonParser.parseString(json).asJsonObject
            val docs = data.getAsJsonObject("response")?.getAsJsonArray("docs") ?: JsonArray()

            val text = buildString {
                appendLine("Maven Central results for: $query")
                appendLine()
                docs.forEach { doc ->
                    val docObj = doc.asJsonObject
                    val g = docObj.get("g")?.asString ?: "?"
                    val a = docObj.get("a")?.asString ?: "?"
                    val latestVersion = docObj.get("latestVersion")?.asString ?: "?"
                    val timestamp = docObj.get("timestamp")?.asLong ?: 0L
                    appendLine("$g:$a")
                    appendLine("  Latest: $latestVersion")
                    if (timestamp > 0L) {
                        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }.format(java.util.Date(timestamp))
                        appendLine("  Updated: $date")
                    }
                    appendLine()
                }
            }
            listOf(UIMessagePart.Text(text.ifEmpty { "No artifacts found for: $query" }))
        },
    )

    private fun httpGet(urlStr: String): String {
        val url = URI(urlStr).toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "Xed-Editor/2.0")
        conn.setRequestProperty("Accept", "application/json")

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) throw RuntimeException("HTTP $responseCode")
        return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
    }
}
