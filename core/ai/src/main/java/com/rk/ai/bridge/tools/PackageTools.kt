package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class NpmSearchTool : BaseMcpTool() {
    override fun getCategory(): String = "Package Management"
    override fun getName(): String = "npm_search"
    override fun getDescription(): String = "Searches npm registry for packages."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "query" to "Package name or search term"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "limit" to "Maximum results (default: 10)"
    )
    override fun getTimeoutMs(): Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val query = requireString(args, "query")
        val limit = (optionalPositiveInt(args, "limit") ?: 10).coerceIn(1, 50)

        val url = "https://registry.npmjs.org/-/v1/search?text=${URLEncoder.encode(query, "UTF-8")}&size=$limit"
        val json = httpGet(url)
        val data = JsonParser.parseString(json).asJsonObject
        val objects = data.getAsJsonArray("objects") ?: JsonArray()

        val text = buildString {
            appendLine("npm search results for: $query")
            appendLine()
            objects.forEach { obj ->
                val pkg = obj.asJsonObject?.getAsJsonObject("package") ?: return@forEach
                val name = pkg.get("name")?.asString ?: "?"
                val version = pkg.get("version")?.asString ?: "?"
                val description = pkg.get("description")?.asString ?: ""
                val publisher = pkg.getAsJsonObject("publisher")?.get("username")?.asString ?: pkg.getAsJsonObject("author")?.get("name")?.asString ?: "?"
                appendLine("$name@$version")
                if (description.isNotBlank()) appendLine("  $description")
                appendLine("  Publisher: $publisher")
                appendLine()
            }
        }

        McpToolResult.success(text.ifEmpty { "No packages found for: $query" })
    }
}

class PipSearchTool : BaseMcpTool() {
    override fun getCategory(): String = "Package Management"
    override fun getName(): String = "pip_search"
    override fun getDescription(): String = "Searches PyPI (Python Package Index) for packages."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "query" to "Package name or search term"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "limit" to "Maximum results (default: 10)"
    )
    override fun getTimeoutMs(): Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val query = requireString(args, "query")
        val limit = (optionalPositiveInt(args, "limit") ?: 10).coerceIn(1, 50)

        val url = "https://pypi.org/simple/"
        val html = httpGet(url)
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

        McpToolResult.success(text.ifEmpty { "No packages found for: $query" })
    }
}

class MavenSearchTool : BaseMcpTool() {
    override fun getCategory(): String = "Package Management"
    override fun getName(): String = "maven_search"
    override fun getDescription(): String = "Searches Maven Central for artifacts."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "query" to "Search term (groupId:artifactId or name)"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "limit" to "Maximum results (default: 10)"
    )
    override fun getTimeoutMs(): Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val query = requireString(args, "query")
        val limit = (optionalPositiveInt(args, "limit") ?: 10).coerceIn(1, 50)

        val url = "https://search.maven.org/solrsearch/select?q=${URLEncoder.encode(query, "UTF-8")}&rows=$limit&wt=json"
        val json = httpGet(url)
        val data = JsonParser.parseString(json).asJsonObject
        val docs = data.getAsJsonObject("response")?.getAsJsonArray("docs") ?: JsonArray()

        val text = buildString {
            appendLine("Maven Central results for: $query")
            appendLine()
            docs.forEach { doc ->
                val obj = doc.asJsonObject
                val g = obj.get("g")?.asString ?: "?"
                val a = obj.get("a")?.asString ?: "?"
                val latestVersion = obj.get("latestVersion")?.asString ?: "?"
                val timestamp = obj.get("timestamp")?.asLong ?: 0
                appendLine("$g:$a")
                appendLine("  Latest: $latestVersion")
                if (timestamp > 0) {
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(java.util.Date(timestamp))
                    appendLine("  Updated: $date")
                }
                appendLine()
            }
        }

        McpToolResult.success(text.ifEmpty { "No artifacts found for: $query" })
    }
}

private fun httpGet(urlStr: String): String {
    val url = URI(urlStr).toURL()
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 20_000
    conn.readTimeout = 20_000
    conn.setRequestProperty("User-Agent", "Xed-Editor/2.0")
    conn.setRequestProperty("Accept", "application/json")

    val responseCode = conn.responseCode
    if (responseCode !in 200..299) throw ToolError.InvalidParam("url", "HTTP $responseCode")
    return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
}
