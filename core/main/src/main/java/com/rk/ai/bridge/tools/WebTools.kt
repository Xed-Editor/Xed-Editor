package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class WebFetchTool : BaseMcpTool() {
    override val name: String = "web_fetch"
    override val description: String = "Fetches a webpage and returns its content as text. Supports markdown conversion, metadata extraction, and code block extraction."
    override val requiredParams: Map<String, String> = mapOf("url" to "string")
    override val optionalParams: Map<String, String> = mapOf(
        "format" to "string",
        "maxLength" to "number",
        "timeoutMs" to "number",
    )
    override val timeoutMs: Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val urlString = requireString(args, "url", maxLength = 8192)
        val format = optionalString(args, "format", "text")
        val maxLength = optionalInt(args, "maxLength", 100_000).coerceIn(1024, 5_000_000)
        val timeoutMs = optionalInt(args, "timeoutMs", 15_000).coerceIn(3_000, 60_000)

        val validated = Security.validateUrl(urlString).getOrElse {
            return McpToolResult.error(it.message ?: "invalid URL", -32602)
        }

        context.pushProgress(0.1f, "Connecting to ${validated.host}...")

        return runCatching {
            val connection = validated.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )

            context.pushProgress(0.3f, "Fetching content...")

            Security.enforceContentLength(connection)
            val contentType = connection.contentType ?: ""

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return McpToolResult.error("HTTP $responseCode: ${connection.responseMessage}", -32000)
            }

            context.pushProgress(0.6f, "Reading response...")

            val reader = BufferedReader(InputStreamReader(
                connection.inputStream, Charsets.UTF_8
            ))
            val text = reader.use { it.readText() }

            connection.disconnect()

            context.pushProgress(0.8f, "Processing content...")

            val result = buildString {
                appendLine("URL: $urlString")
                appendLine("Status: $responseCode")
                appendLine("Content-Type: $contentType")
                appendLine("Size: ${text.length} bytes")
                appendLine()

                val displayText = when {
                    contentType.contains("text/html", true) -> {
                        val stripped = stripHtmlTags(text)
                        if (format == "markdown") htmlToMarkdown(text) else stripped
                    }
                    contentType.contains("application/json", true) -> {
                        formatJson(text)
                    }
                    else -> text
                }

                append(displayText.take(maxLength))
                if (displayText.length > maxLength) {
                    append("\n\n... content truncated at ${maxLength / 1024}KB")
                }
            }

            context.pushProgress(1f, "Done")
            resultText(enforceOutputLimit(result))
        }.getOrElse { e ->
            McpToolResult.error("Fetch failed: ${e.message ?: "unknown error"}", -32000)
        }
    }

    private fun stripHtmlTags(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun htmlToMarkdown(html: String): String {
        var text = html
        text = text.replace(Regex("<h1[^>]*>(.*?)</h1>", RegexOption.IGNORE_CASE)) { "# ${it.groupValues[1]}\n" }
        text = text.replace(Regex("<h2[^>]*>(.*?)</h2>", RegexOption.IGNORE_CASE)) { "## ${it.groupValues[1]}\n" }
        text = text.replace(Regex("<h3[^>]*>(.*?)</h3>", RegexOption.IGNORE_CASE)) { "### ${it.groupValues[1]}\n" }
        text = text.replace(Regex("<li[^>]*>(.*?)</li>", RegexOption.IGNORE_CASE)) { "- ${it.groupValues[1]}\n" }
        text = text.replace(Regex("<code[^>]*>(.*?)</code>", RegexOption.IGNORE_CASE)) { "`${it.groupValues[1]}`" }
        text = text.replace(Regex("<pre[^>]*>(.*?)</pre>", RegexOption.IGNORE_CASE)) { "```\n${it.groupValues[1]}\n```\n" }
        text = text.replace(Regex("<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.IGNORE_CASE)) { "[${it.groupValues[2]}](${it.groupValues[1]})" }
        text = text.replace(Regex("<p[^>]*>(.*?)</p>", RegexOption.IGNORE_CASE)) { "${it.groupValues[1]}\n\n" }
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<strong[^>]*>(.*?)</strong>", RegexOption.IGNORE_CASE)) { "**${it.groupValues[1]}**" }
        text = text.replace(Regex("<em[^>]*>(.*?)</em>", RegexOption.IGNORE_CASE)) { "*${it.groupValues[1]}*" }
        return stripHtmlTags(text).trim()
    }

    private fun formatJson(text: String): String {
        return runCatching {
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            val element = com.google.gson.JsonParser.parseString(text)
            gson.toJson(element)
        }.getOrDefault(text)
    }
}

class WebSearchTool : BaseMcpTool() {
    override val name: String = "web_search"
    override val description: String = "Searches the web using Google and returns top results with titles, snippets, and URLs."
    override val requiredParams: Map<String, String> = mapOf("query" to "string")
    override val optionalParams: Map<String, String> = mapOf("numResults" to "number")
    override val timeoutMs: Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = requireString(args, "query", maxLength = 500)
        val numResults = optionalInt(args, "numResults", 8).coerceIn(1, 20)

        context.pushProgress(0.1f, "Searching for \"$query\"...")

        return runCatching {
            val searchUrl = URL("https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&num=$numResults")
            val connection = searchUrl.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )

            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            val html = reader.use { it.readText() }
            connection.disconnect()

            context.pushProgress(0.5f, "Extracting results...")

            val results = extractGoogleResults(html, numResults)
            context.pushProgress(0.9f, "Found ${results.size} results")

            val output = buildString {
                appendLine("Search results for: $query")
                appendLine("Found ${results.size} results")
                appendLine()
                results.forEachIndexed { i, result ->
                    appendLine("${i + 1}. ${result.title}")
                    appendLine("   URL: ${result.url}")
                    if (result.snippet.isNotBlank()) {
                        appendLine("   ${result.snippet}")
                    }
                    appendLine()
                }
            }

            context.pushProgress(1f, "Done")
            resultText(enforceOutputLimit(output))
        }.getOrElse { e ->
            McpToolResult.error("Search failed: ${e.message ?: "unknown error"}", -32000)
        }
    }

    private data class SearchResult(val title: String, val url: String, val snippet: String)

    private fun extractGoogleResults(html: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        // Try extracting from modern Google HTML
        val anchorPattern = Regex("<a[^>]*href=\"/url\\?q=([^&\"]+)[^\"]*\"[^>]*>(.*?)</a>", RegexOption.IGNORE_CASE)
        val anchorMatches = anchorPattern.findAll(html).toList()

        val snippetPattern = Regex("<div[^>]*class=\"[^\"]*VwiC3b[^\"]*\"[^>]*>(.*?)</div>", RegexOption.IGNORE_CASE)
        val snippetMatches = snippetPattern.findAll(html).toList()

        anchorMatches.forEachIndexed { index, match ->
            if (index >= maxResults) return@forEachIndexed
            val url = match.groupValues[1].let {
                java.net.URLDecoder.decode(it, "UTF-8")
                    .substringBefore("&sa=")
                    .substringBefore("&ved=")
            }
            val title = stripHtmlTags(match.groupValues[2])
            val snippet = snippetMatches.getOrNull(index)?.let {
                stripHtmlTags(it.groupValues[1])
            } ?: ""
            if (url.isNotBlank() && title.isNotBlank()) {
                results.add(SearchResult(title, url, snippet))
            }
        }

        return results
    }

    private fun stripHtmlTags(html: String): String {
        return html.replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

class ScrapePageTool : BaseMcpTool() {
    override val name: String = "scrape_page"
    override val description: String = "Extracts structured content from a webpage: readable text, metadata, and code blocks."
    override val requiredParams: Map<String, String> = mapOf("url" to "string")
    override val optionalParams: Map<String, String> = mapOf("extractCode" to "boolean", "maxLength" to "number")
    override val timeoutMs: Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val urlString = requireString(args, "url", maxLength = 8192)
        val extractCode = optionalBoolean(args, "extractCode", true)
        val maxLength = optionalInt(args, "maxLength", 200_000).coerceIn(1024, 5_000_000)

        val validated = Security.validateUrl(urlString).getOrElse {
            return McpToolResult.error(it.message ?: "invalid URL", -32602)
        }

        context.pushProgress(0.2f, "Fetching page...")

        return runCatching {
            val connection = validated.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"
            )

            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            val html = reader.use { it.readText() }
            connection.disconnect()

            context.pushProgress(0.5f, "Extracting content...")

            val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.let { stripHtmlTags(it) } ?: ""
            val description = Regex("<meta[^>]*name=\"description\"[^>]*content=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.let { stripHtmlTags(it) } ?: ""

            val codeBlocks = if (extractCode) {
                Regex("<pre[^>]*>(.*?)</pre>", RegexOption.IGNORE_CASE + RegexOption.DOT_MATCHES_ALL)
                    .findAll(html)
                    .map { stripHtmlTags(it.groupValues[1]).trim() }
                    .filter { it.length > 20 }
                    .toList()
            } else emptyList()

            val textContent = stripHtmlTags(html)
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()

            context.pushProgress(0.8f, "Formatting output...")

            val output = buildString {
                appendLine("Title: $title")
                if (description.isNotBlank()) appendLine("Description: $description")
                appendLine("URL: $urlString")
                appendLine()

                if (codeBlocks.isNotEmpty()) {
                    appendLine("--- Code Blocks (${codeBlocks.size}) ---")
                    codeBlocks.take(10).forEachIndexed { i, code ->
                        appendLine("```")
                        appendLine(code.take(5000))
                        if (code.length > 5000) appendLine("... (truncated)")
                        appendLine("```")
                        appendLine()
                    }
                }

                appendLine("--- Content ---")
                append(textContent.take(maxLength))
                if (textContent.length > maxLength) {
                    append("\n\n... content truncated at ${maxLength / 1024}KB")
                }
            }

            context.pushProgress(1f, "Done")
            resultText(enforceOutputLimit(output))
        }.getOrElse { e ->
            McpToolResult.error("Scrape failed: ${e.message ?: "unknown error"}", -32000)
        }
    }

    private fun stripHtmlTags(html: String): String {
        return html.replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
