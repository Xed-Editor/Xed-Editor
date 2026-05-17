package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.service.IdeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class WebSearchTool : BaseMcpTool() {
    override fun getName(): String = "webSearch"
    override fun getDescription(): String = "Performs a web search using DuckDuckGo API and returns formatted results."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "numResults" to "number",
        "region" to "string"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "query" to "Search query string"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "numResults" to "Number of results (default: 10, max: 20)",
        "region" to "Region for search (default: all)"
    )
    override fun getTimeoutMs(): Long = 30_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val query = requireString(args, "query")
        val numResults = optionalInt(args, "numResults", 10)?.coerceIn(1, 20) ?: 10
        val region = optionalString(args, "region", "all")

        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                val results = parseDuckDuckGoResults(body, numResults)
                jsonResult(JsonObject().apply {
                    addProperty("query", query)
                    addProperty("count", results.size())
                    add("results", results)
                })
            } catch (e: Exception) {
                jsonResult(JsonObject().apply {
                    addProperty("error", "Search failed: ${e.message}")
                    addProperty("query", query)
                })
            }
        }
    }

    private fun parseDuckDuckGoResults(json: String, limit: Int): JsonArray {
        val results = JsonArray()
        try {
            val parser = com.google.gson.JsonParser.parseString(json)
            val root = parser.asJsonObject
            val relatedTopics = root.getAsJsonArray("RelatedTopics")

            if (relatedTopics != null) {
                var count = 0
                relatedTopics.forEach { element ->
                    if (count >= limit) return@forEach
                    val topic = element.asJsonObject
                    val text = topic.get("Text")?.asString ?: return@forEach
                    val url = topic.get("URL")?.asString ?: return@forEach

                    val title = text.substringBefore(" - ").take(200)
                    val snippet = text.substringAfter(" - ", text).take(500)

                    results.add(JsonObject().apply {
                        addProperty("title", title)
                        addProperty("url", url)
                        addProperty("snippet", snippet)
                    })
                    count++
                }
            }
        } catch (e: Exception) {
            // Fallback - try to extract from raw HTML
        }
        return results
    }
}

class WebFetchTool : BaseMcpTool() {
    override fun getName(): String = "webFetch"
    override fun getDescription(): String = "Fetches content from a specified URL. Supports HTML, JSON, markdown, and plain text."
    override fun getRequiredParams(): Map<String, String> = mapOf("url" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "format" to "string",
        "maxLength" to "number",
        "extractText" to "boolean"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "url" to "Full URL to fetch"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "format" to "Output format: 'markdown' (default), 'text', 'html', 'json'",
        "maxLength" to "Max characters to return (default: 50000, max: 100000)",
        "extractText" to "Extract text only from HTML (default: true)"
    )
    override fun getTimeoutMs(): Long = 45_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val url = requireString(args, "url")
        val format = optionalString(args, "format", "markdown")
        val maxLength = optionalInt(args, "maxLength", 50000)?.coerceIn(1000, 100000) ?: 50000
        val extractText = optionalBoolean(args, "extractText", true)

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw ToolError.InvalidParam("URL must start with http:// or https://")
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/json,text/markdown,*/*")
                    .build()

                val response = client.newCall(request).execute()
                val contentType = response.header("Content-Type", "").lowercase()
                var body = response.body?.string() ?: ""

                val result = when {
                    contentType.contains("json") || format == "json" -> {
                        try {
                            val json = com.google.gson.JsonParser.parseString(body)
                            body = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json)
                        } catch (e: Exception) {
                            // Return as-is if not valid JSON
                        }
                        formatBody(body, "text", maxLength)
                    }
                    contentType.contains("html") || format == "html" -> {
                        if (extractText) {
                            val text = extractTextFromHtml(body)
                            formatBody(text, "markdown", maxLength)
                        } else {
                            formatBody(body, "html", maxLength)
                        }
                    }
                    else -> formatBody(body, format, maxLength)
                }

                jsonResult(JsonObject().apply {
                    addProperty("url", url)
                    addProperty("status", response.code)
                    addProperty("contentType", contentType)
                    add("content", com.google.gson.JsonPrimitive(result))
                })
            } catch (e: Exception) {
                jsonResult(JsonObject().apply {
                    addProperty("error", "Fetch failed: ${e.message}")
                    addProperty("url", url)
                })
            }
        }
    }

    private fun formatBody(content: String, format: String, maxLength: Int): String {
        var result = content
        if (result.length > maxLength) {
            result = result.take(maxLength) + "\n\n... (truncated)"
        }
        return result
    }

    private fun extractTextFromHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<header[^>]*>.*?</header>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<footer[^>]*>.*?</footer>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<nav[^>]*>.*?</nav>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#39;"), "'")
            .trim()
    }
}

class WebScrapeTool : BaseMcpTool() {
    override fun getName(): String = "webScrape"
    override fun getDescription(): String = "Scrapes structured data from web pages using CSS-like selectors. Useful for extracting specific content from websites."
    override fun getRequiredParams(): Map<String, String> = mapOf(
        "url" to "string",
        "selector" to "string"
    )
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "attribute" to "string",
        "maxItems" to "number"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "url" to "URL to scrape",
        "selector" to "CSS selector or XPath to match elements"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "attribute" to "Extract specific attribute (e.g., 'href', 'src') instead of text",
        "maxItems" to "Maximum number of elements to extract (default: 10, max: 50)"
    )
    override fun getTimeoutMs(): Long = 45_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val url = requireString(args, "url")
        val selector = requireString(args, "selector")
        val attribute = optionalString(args, "attribute")
        val maxItems = optionalInt(args, "maxItems", 10)?.coerceIn(1, 50) ?: 10

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: ""

                val results = scrapeHtml(html, selector, attribute, maxItems)

                jsonResult(JsonObject().apply {
                    addProperty("url", url)
                    addProperty("selector", selector)
                    addProperty("count", results.size())
                    add("results", results)
                })
            } catch (e: Exception) {
                jsonResult(JsonObject().apply {
                    addProperty("error", "Scrape failed: ${e.message}")
                    addProperty("url", url)
                })
            }
        }
    }

    private fun scrapeHtml(html: String, selector: String, attribute: String?, maxItems: Int): JsonArray {
        val results = JsonArray()

        // Simple CSS-like selector parsing (basic implementation)
        // Supports: tag, .class, #id, [attribute], tag.class, tag#id
        val tagPattern = selector.substringBefore(".").substringBefore("#").substringBefore("[").trim()
        val classPattern = selector.substringAfter(".").substringBefore("#").substringBefore("[").trim()
        val idPattern = if (selector.contains("#")) {
            selector.substringAfter("#").substringBefore(".").substringBefore("[").trim()
        } else null

        // Find all matching tags
        val tagRegex = Regex("<($tagPattern)[^>]*>([^<]*)</$tagPattern>", RegexOption.IGNORE_CASE)
        val matches = tagRegex.findAll(html).take(maxItems)

        matches.forEach { match ->
            val content = match.groupValues[2].trim()
            if (content.isNotBlank()) {
                if (attribute != null) {
                    // Try to extract attribute from opening tag
                    val openTag = html.substring(maxOf(0, match.range.first - 200), match.range.first)
                        .takeLast(200)
                    val attrRegex = Regex("$attribute=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                    val attrMatch = attrRegex.find(openTag)
                    if (attrMatch != null) {
                        results.add(com.google.gson.JsonPrimitive(attrMatch.groupValues[1]))
                    }
                } else {
                    results.add(com.google.gson.JsonPrimitive(content))
                }
            }
        }

        // If no tag matches, try finding by id or class
        if (results.size() == 0 && (idPattern != null || classPattern.isNotBlank())) {
            val searchPattern = when {
                idPattern != null -> "id=[\"']?$idPattern[\"']?"
                classPattern.isNotBlank() -> "class=[\"']?$classPattern[\"']?"
                else -> null
            }

            if (searchPattern != null) {
                val elementRegex = Regex("<([a-zA-Z][a-zA-Z0-9]*)[^>]*$searchPattern[^>]*>([^<]*)</([a-zA-Z][a-zA-Z0-9]*)>", RegexOption.IGNORE_CASE)
                elementRegex.findAll(html).take(maxItems).forEach { match ->
                    val content = match.groupValues[2].trim()
                    if (content.isNotBlank()) {
                        results.add(com.google.gson.JsonPrimitive(content))
                    }
                }
            }
        }

        return results
    }
}