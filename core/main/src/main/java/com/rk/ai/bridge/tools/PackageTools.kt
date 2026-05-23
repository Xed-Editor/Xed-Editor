package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

abstract class BasePackageTool : BaseMcpTool() {
    override val timeoutMs: Long = 20_000L

    protected fun httpGetText(urlString: String): Result<String> = runCatching {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "Xed-Editor-Bridge/1.0")
        connection.setRequestProperty("Accept", "application/json")

        connection.responseCode.let { code ->
            if (code != 200) throw RuntimeException("HTTP $code: ${connection.responseMessage}")
        }

        val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
        val text = reader.use { it.readText() }
        connection.disconnect()
        text
    }
}

class NpmSearchTool : BasePackageTool() {
    override val name: String = "npm_search"
    override val description: String = "Searches the npm registry for packages."
    override val requiredParams: Map<String, String> = mapOf("query" to "string")
    override val optionalParams: Map<String, String> = mapOf("limit" to "number")

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = requireString(args, "query", maxLength = 200)
        val limit = optionalInt(args, "limit", 10).coerceIn(1, 30)

        context.pushProgress(0.3f, "Searching npm for \"$query\"...")

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val result = httpGetText("https://registry.npmjs.org/-/v1/search?text=$encoded&size=$limit")

        return result.fold(
            onSuccess = { text ->
                context.pushProgress(0.7f, "Formatting results...")
                val json = JsonParser.parseString(text).asJsonObject
                val total = json.get("total")?.asInt ?: 0
                val objects = json.getAsJsonArray("objects") ?: JsonArray()

                val output = buildString {
                    appendLine("npm search: $query")
                    appendLine("Total results: $total")
                    appendLine()

                    objects.forEach { obj ->
                        val pkg = obj.asJsonObject.getAsJsonObject("package")
                        val name = pkg.get("name")?.asString ?: "?"
                        val version = pkg.get("version")?.asString ?: "?"
                        val description = pkg.get("description")?.asString ?: ""
                        val publisher = pkg.getAsJsonObject("publisher")?.get("username")?.asString ?: "?"
                        val keywords = pkg.getAsJsonArray("keywords")?.joinToString(", ") ?: ""
                        val npmUrl = "https://www.npmjs.com/package/$name"

                        appendLine("$name@$version")
                        appendLine("  $description")
                        appendLine("  Publisher: $publisher")
                        if (keywords.isNotBlank()) appendLine("  Keywords: $keywords")
                        appendLine("  URL: $npmUrl")
                        appendLine()
                    }
                }

                context.pushProgress(1f, "Done")
                resultText(enforceOutputLimit(output))
            },
            onFailure = { e ->
                McpToolResult.error("npm search failed: ${e.message}", -32000)
            }
        )
    }
}

class PipSearchTool : BasePackageTool() {
    override val name: String = "pip_search"
    override val description: String = "Searches PyPI (Python Package Index) for packages."
    override val requiredParams: Map<String, String> = mapOf("query" to "string")
    override val optionalParams: Map<String, String> = mapOf("limit" to "number")

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = requireString(args, "query", maxLength = 200)
        val limit = optionalInt(args, "limit", 10).coerceIn(1, 30)

        context.pushProgress(0.3f, "Searching PyPI for \"$query\"...")

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val result = httpGetText("https://pypi.org/search/api/?q=$encoded&per_page=$limit")

        return result.fold(
            onSuccess = { text ->
                context.pushProgress(0.7f, "Formatting results...")
                val json = JsonParser.parseString(text).asJsonObject
                val total = json.get("total")?.asInt ?: 0
                val items = json.getAsJsonArray("items") ?: JsonArray()

                val output = buildString {
                    appendLine("PyPI search: $query")
                    appendLine("Total results: $total")
                    appendLine()

                    items.forEach { item ->
                        val obj = item.asJsonObject
                        val name = obj.get("name")?.asString ?: "?"
                        val version = obj.get("version")?.asString ?: "?"
                        val description = obj.get("description")?.asString ?: ""
                        val url = obj.get("url")?.asString ?: ""
                        val author = obj.get("author")?.asString ?: "?"

                        appendLine("$name $version")
                        appendLine("  $description")
                        appendLine("  Author: $author")
                        appendLine("  URL: $url")
                        appendLine()
                    }
                }

                context.pushProgress(1f, "Done")
                resultText(enforceOutputLimit(output))
            },
            onFailure = { e ->
                McpToolResult.error("PyPI search failed: ${e.message}", -32000)
            }
        )
    }
}

class MavenSearchTool : BasePackageTool() {
    override val name: String = "maven_search"
    override val description: String = "Searches Maven Central for artifacts. Returns groupId, artifactId, version, and description."
    override val requiredParams: Map<String, String> = mapOf("query" to "string")
    override val optionalParams: Map<String, String> = mapOf(
        "limit" to "number",
        "groupId" to "string",
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = requireString(args, "query", maxLength = 200)
        val limit = optionalInt(args, "limit", 10).coerceIn(1, 30)
        val groupId = optionalString(args, "groupId")

        context.pushProgress(0.3f, "Searching Maven Central for \"$query\"...")

        val encodedQuery = java.net.URLEncoder.encode(
            if (groupId.isNotBlank()) "g:$groupId AND $query" else query, "UTF-8"
        )
        val result = httpGetText(
            "https://search.maven.org/solrsearch/select?q=$encodedQuery&rows=$limit&wt=json"
        )

        return result.fold(
            onSuccess = { text ->
                context.pushProgress(0.7f, "Formatting results...")
                val json = JsonParser.parseString(text).asJsonObject
                val response = json.getAsJsonObject("response")
                val total = response?.get("numFound")?.asInt ?: 0
                val docs = response?.getAsJsonArray("docs") ?: JsonArray()

                val output = buildString {
                    appendLine("Maven Central search: $query")
                    appendLine("Total results: $total")
                    appendLine()

                    docs.forEach { doc ->
                        val obj = doc.asJsonObject
                        val g = obj.get("g")?.asString ?: "?"
                        val a = obj.get("a")?.asString ?: "?"
                        val v = obj.get("latestVersion")?.asString ?: obj.get("v")?.asString ?: "?"
                        val description = obj.get("description")?.asString ?: obj.get("s")?.asString ?: ""

                        appendLine("$g:$a:$v")
                        if (description.isNotBlank()) appendLine("  $description")
                        appendLine("  Dependency:")
                        appendLine("  implementation(\"$g:$a:$v\")")
                        appendLine()
                    }
                }

                context.pushProgress(1f, "Done")
                resultText(enforceOutputLimit(output))
            },
            onFailure = { e ->
                McpToolResult.error("Maven search failed: ${e.message}", -32000)
            }
        )
    }
}

class GradleDependencySearchTool : BasePackageTool() {
    override val name: String = "gradle_dependency_search"
    override val description: String = "Searches for Gradle dependencies from Maven Central. Returns ready-to-use implementation() snippets."
    override val requiredParams: Map<String, String> = mapOf("query" to "string")
    override val optionalParams: Map<String, String> = mapOf("limit" to "number")

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = requireString(args, "query", maxLength = 200)
        val limit = optionalInt(args, "limit", 10).coerceIn(1, 30)

        context.pushProgress(0.3f, "Searching Maven Central for \"$query\"...")

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val result = httpGetText(
            "https://search.maven.org/solrsearch/select?q=$encoded&rows=$limit&wt=json"
        )

        return result.fold(
            onSuccess = { text ->
                context.pushProgress(0.7f, "Formatting results...")
                val json = JsonParser.parseString(text).asJsonObject
                val response = json.getAsJsonObject("response")
                val total = response?.get("numFound")?.asInt ?: 0
                val docs = response?.getAsJsonArray("docs") ?: JsonArray()

                val output = buildString {
                    appendLine("Gradle dependency search: $query")
                    appendLine("Total results: $total")
                    appendLine()

                    docs.forEach { doc ->
                        val obj = doc.asJsonObject
                        val g = obj.get("g")?.asString ?: "?"
                        val a = obj.get("a")?.asString ?: "?"
                        val v = obj.get("latestVersion")?.asString ?: obj.get("v")?.asString ?: "?"
                        val description = obj.get("description")?.asString ?: obj.get("s")?.asString ?: ""
                        val tags = listOfNotNull(
                            obj.get("tags")?.asString,
                            obj.get("ec")?.asString,
                        ).filter { it.isNotBlank() }.joinToString(", ")

                        appendLine("$g:$a:$v")
                        if (description.isNotBlank()) appendLine("  $description")
                        if (tags.isNotBlank()) appendLine("  Tags: $tags")
                        appendLine("  Gradle:")
                        appendLine("  implementation(\"$g:$a:$v\")")
                        appendLine()
                    }
                }

                context.pushProgress(1f, "Done")
                resultText(enforceOutputLimit(output))
            },
            onFailure = { e ->
                McpToolResult.error("Search failed: ${e.message}", -32000)
            }
        )
    }
}
