package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

class WebFetchTool : BaseMcpTool() {
    override fun getName(): String = "web_fetch"
    override fun getDescription(): String = "Fetches content from a specified URL. Returns the content as text."
    override fun getRequiredParams(): Map<String, String> = mapOf("url" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "format" to "string",
        "timeout" to "number"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "url" to "The URL to fetch content from"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "format" to "Response format: 'text', 'markdown', or 'html' (default: text)",
        "timeout" to "Timeout in seconds (default: 30)"
    )
    override fun getTimeoutMs(): Long = 60_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val urlStr = requireString(args, "url")
        val format = optionalString(args, "format", "text")
        val timeoutSec = optionalInt(args, "timeout") ?: 30

        val url = validateUrl(urlStr) ?: throw ToolError.InvalidParam("url", "invalid or unsafe URL: $urlStr")

        val result = fetchUrl(url, timeoutSec)
        val text = when (format) {
            "markdown" -> htmlToMarkdown(result)
            "html" -> result
            else -> stripHtml(result)
        }

        val truncated = if (text.length > context.maxOutputSize.toInt())
            text.take(context.maxOutputSize.toInt()) + "\n... (truncated at ${context.maxOutputSize / 1024}KB)"
        else text

        McpToolResult.success(truncated, mapOf(
            "url" to urlStr,
            "format" to format,
            "size" to text.length.toString()
        ))
    }

    private fun fetchUrl(url: URL, timeoutSec: Int): String {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutSec * 1000
        conn.readTimeout = timeoutSec * 1000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Xed-Editor/2.0")
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

        val responseCode = conn.responseCode
        if (responseCode in 300..399) {
            val redirectUrl = conn.getHeaderField("Location") ?: throw ToolError.InvalidParam("url", "redirect with no Location header")
            return fetchUrl(URI(redirectUrl).toURL(), timeoutSec)
        }

        val reader = BufferedReader(InputStreamReader(
            if (responseCode in 200..299) conn.inputStream else conn.errorStream,
            conn.contentEncoding?.takeIf { it.isNotBlank() }?.let { Charsets.UTF_8 } ?: Charsets.UTF_8
        ))
        return reader.readText()
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500_000)
    }

    private fun htmlToMarkdown(html: String): String {
        var md = html
        md = md.replace(Regex("<h1[^>]*>(.*?)</h1>", RegexOption.DOT_MATCHES_ALL)) { "\n# ${it.groupValues[1]}\n" }
        md = md.replace(Regex("<h2[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL)) { "\n## ${it.groupValues[1]}\n" }
        md = md.replace(Regex("<h3[^>]*>(.*?)</h3>", RegexOption.DOT_MATCHES_ALL)) { "\n### ${it.groupValues[1]}\n" }
        md = md.replace(Regex("<h4[^>]*>(.*?)</h4>", RegexOption.DOT_MATCHES_ALL)) { "\n#### ${it.groupValues[1]}\n" }
        md = md.replace(Regex("<strong>(.*?)</strong>", RegexOption.DOT_MATCHES_ALL)) { "**${it.groupValues[1]}**" }
        md = md.replace(Regex("<em>(.*?)</em>", RegexOption.DOT_MATCHES_ALL)) { "*${it.groupValues[1]}*" }
        md = md.replace(Regex("<code>(.*?)</code>", RegexOption.DOT_MATCHES_ALL)) { "`${it.groupValues[1]}`" }
        md = md.replace(Regex("<pre><code>(.*?)</code></pre>", RegexOption.DOT_MATCHES_ALL)) { "\n```\n${it.groupValues[1]}\n```\n" }
        md = md.replace(Regex("<a[^>]*href=\"(.*?)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)) { "[${it.groupValues[2]}](${it.groupValues[1]})" }
        md = md.replace(Regex("<li>(.*?)</li>", RegexOption.DOT_MATCHES_ALL)) { "- ${it.groupValues[1]}\n" }
        md = md.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        md = md.replace(Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)) { "\n${it.groupValues[1]}\n" }
        md = stripHtml(md)
        md = md.replace(Regex("\n{3,}"), "\n\n")
        return md.trim()
    }
}

class WebSearchTool : BaseMcpTool() {
    override fun getName(): String = "web_search"
    override fun getDescription(): String = "Searches the web using a search engine. Returns search results with snippets."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("numResults" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "query" to "Search query"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "numResults" to "Number of search results to return (default: 8)"
    )
    override fun getTimeoutMs(): Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val query = requireString(args, "query")
        val numResults = optionalPositiveInt(args, "numResults") ?: 8

        val results = searchDuckDuckGo(query, numResults)
        val text = buildString {
            results.forEachIndexed { i, r ->
                append("${i + 1}. ${r.title}\n")
                append("   URL: ${r.url}\n")
                append("   ${r.snippet}\n\n")
            }
        }

        McpToolResult.success(text.ifEmpty { "No results found for: $query" })
    }

    private data class SearchResult(val title: String, val url: String, val snippet: String)

    private fun searchDuckDuckGo(query: String, num: Int): List<SearchResult> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URI("https://html.duckduckgo.com/html/?q=$encodedQuery").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            val html = conn.inputStream.bufferedReader().readText()

            val results = mutableListOf<SearchResult>()
            val titleRegex = Regex("<a[^>]*class=\"result__a\"[^>]*href=\"(.*?)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            val snippetRegex = Regex("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)

            val titles = titleRegex.findAll(html).toList()
            val snippets = snippetRegex.findAll(html).toList()

            for (i in 0 until minOf(titles.size, snippets.size, num)) {
                val t = titles[i]
                var href = t.groupValues[1].trim()
                if (href.startsWith("//")) href = "https:$href"
                val title = stripHtmlTags(t.groupValues[2]).trim()
                val snippet = stripHtmlTags(snippets[i].groupValues[1]).trim()
                results.add(SearchResult(title, href, snippet))
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun stripHtmlTags(text: String): String {
        return text.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()
    }
}

private fun validateUrl(urlStr: String): URL? {
    return try {
        val url = URI(urlStr).toURL()
        val protocol = url.protocol.lowercase()
        if (protocol != "http" && protocol != "https") return null
        val host = url.host.lowercase()
        if (host.isBlank()) return null
        // Block localhost/private IPs for security
        if (host == "127.0.0.1" || host == "localhost" || host == "[::1]" || host == "0.0.0.0") return null
        url
    } catch (e: Exception) { null }
}
