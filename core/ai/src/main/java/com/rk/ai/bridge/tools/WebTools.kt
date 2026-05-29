package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset

class WebFetchTool : BaseMcpTool() {
    override fun getCategory(): String = "Web"
    override fun getName(): String = "web_fetch"
    override fun getDescription(): String = "Fetches and extracts readable content from a URL. Supports text, HTML, JSON, XML, and markdown output. Use web_download for binary files."
    override fun getRequiredParams(): Map<String, String> = mapOf("url" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "format" to "string",
        "timeout" to "number",
        "maxBytes" to "number"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "url" to "The URL to fetch content from"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "format" to "Response format: 'text', 'markdown', 'html', or 'raw' (default: text)",
        "timeout" to "Timeout in seconds (default: 30, max: 60)",
        "maxBytes" to "Maximum response bytes to read (default: 5MB, max: 20MB)"
    )
    override fun getTimeoutMs(): Long = 60_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val urlStr = requireString(args, "url")
        val format = optionalString(args, "format", "text").lowercase()
        val timeoutSec = optionalInt(args, "timeout") ?: 30
        val maxBytes = (optionalPositiveInt(args, "maxBytes") ?: context.maxOutputSize.toInt()).coerceIn(1, 20 * 1024 * 1024)
        val url = validateUrl(urlStr) ?: throw ToolError.InvalidParam("url", "invalid or unsafe URL: $urlStr")

        val response = fetchBytes(url, timeoutSec, maxBytes)
        if (!isReadableContent(response.contentType) && format != "raw") {
            return@withContext McpToolResult.success(
                "Binary or unsupported content type '${response.contentType}'. Use web_download to save this file.",
                mapOf("url" to response.finalUrl, "contentType" to response.contentType, "bytes" to response.bytes.size)
            )
        }

        val source = response.bytes.toString(response.charset())
        val text = when (format) {
            "html", "raw" -> source
            "markdown" -> htmlToMarkdown(source, response.finalUrl)
            else -> if (looksLikeHtml(response.contentType, source)) stripHtml(source) else source
        }
        val truncated = text.limitForTool(context.maxOutputSize.toInt())
        McpToolResult.success(
            truncated,
            mapOf(
                "url" to response.finalUrl,
                "format" to format,
                "contentType" to response.contentType,
                "bytes" to response.bytes.size,
                "truncated" to (truncated.length < text.length)
            )
        )
    }
}

class WebDownloadTool : BaseMcpTool() {
    override fun getCategory(): String = "Web"
    override fun getName(): String = "web_download"
    override fun getDescription(): String = "Downloads a URL to a workspace file. Creates parent directories automatically and preserves binary content."
    override fun getRequiredParams(): Map<String, String> = mapOf("url" to "string", "outputPath" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("timeout" to "number", "maxBytes" to "number", "overwrite" to "boolean")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "url" to "URL to download",
        "outputPath" to "Workspace file path or existing directory where the download should be saved"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "timeout" to "Timeout in seconds (default: 60, max: 120)",
        "maxBytes" to "Maximum bytes to download (default: 100MB, max: 500MB)",
        "overwrite" to "Overwrite the output file if it exists (default: false)"
    )
    override fun getTimeoutMs(): Long = 120_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val urlStr = requireString(args, "url")
        val outputPath = requireString(args, "outputPath")
        val timeoutSec = optionalInt(args, "timeout") ?: 60
        val maxBytes = (optionalPositiveInt(args, "maxBytes") ?: 100 * 1024 * 1024).coerceIn(1, 500 * 1024 * 1024)
        val overwrite = optionalBoolean(args, "overwrite")
        val url = validateUrl(urlStr) ?: throw ToolError.InvalidParam("url", "invalid or unsafe URL: $urlStr")
        val targetBase = resolvePathOrThrow(context, outputPath)

        val response = fetchBytes(url, timeoutSec.coerceIn(1, 120), maxBytes)
        val target = if (targetBase.exists() && targetBase.isDirectory) {
            File(targetBase, response.fileNameFromHeaders() ?: url.fileNameFromPath() ?: "download.bin")
        } else {
            targetBase
        }
        if (target.exists() && !overwrite) throw ToolError.InvalidParam("outputPath", "file already exists: ${target.absolutePath}")
        target.parentFile?.mkdirs()
        target.writeBytes(response.bytes)
        McpToolResult.success(
            "Downloaded ${response.bytes.size} bytes to ${target.absolutePath}",
            mapOf("url" to response.finalUrl, "path" to target.absolutePath, "contentType" to response.contentType, "bytes" to response.bytes.size)
        )
    }
}

class WebSearchTool : BaseMcpTool() {
    override fun getCategory(): String = "Web"
    override fun getName(): String = "web_search"
    override fun getDescription(): String = "Searches the web using DuckDuckGo HTML results and returns titles, URLs, and snippets."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("numResults" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf("query" to "Search query")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf("numResults" to "Number of search results to return (default: 8, max: 20)")
    override fun getTimeoutMs(): Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val query = requireString(args, "query")
        val numResults = (optionalPositiveInt(args, "numResults") ?: 8).coerceIn(1, 20)
        val results = searchDuckDuckGo(query, numResults)
        val text = results.joinToString("\n\n") { r ->
            "${r.rank}. ${r.title}\nURL: ${r.url}\n${r.snippet}"
        }
        McpToolResult.success(text.ifEmpty { "No results found for: $query" }, mapOf("query" to query, "results" to results.size))
    }
}

class WebResearchTool : BaseMcpTool() {
    override fun getCategory(): String = "Web"
    override fun getName(): String = "web_research"
    override fun getDescription(): String = "Searches the web and optionally fetches top result pages for research. Returns sources plus readable excerpts."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "numResults" to "number",
        "fetchPages" to "boolean",
        "pageChars" to "number",
        "timeout" to "number"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf("query" to "Research query")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "numResults" to "Number of search results to inspect (default: 5, max: 10)",
        "fetchPages" to "Fetch readable excerpts from result pages (default: true)",
        "pageChars" to "Characters to keep per fetched page (default: 4000, max: 20000)",
        "timeout" to "Timeout per page in seconds (default: 20, max: 60)"
    )
    override fun getTimeoutMs(): Long = 120_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val query = requireString(args, "query")
        val numResults = (optionalPositiveInt(args, "numResults") ?: 5).coerceIn(1, 10)
        val fetchPages = optionalBoolean(args, "fetchPages", true)
        val pageChars = (optionalPositiveInt(args, "pageChars") ?: 4000).coerceIn(500, 20_000)
        val timeoutSec = (optionalInt(args, "timeout") ?: 20).coerceIn(1, 60)
        val results = searchDuckDuckGo(query, numResults)

        val text = buildString {
            appendLine("Research query: $query")
            appendLine("Sources: ${results.size}")
            appendLine()
            results.forEach { result ->
                appendLine("${result.rank}. ${result.title}")
                appendLine("URL: ${result.url}")
                if (result.snippet.isNotBlank()) appendLine("Snippet: ${result.snippet}")
                if (fetchPages) {
                    appendLine("Excerpt:")
                    val excerpt = runCatching {
                        val safeUrl = validateUrl(result.url) ?: throw ToolError.InvalidParam("url", "unsafe result URL")
                        val response = fetchBytes(safeUrl, timeoutSec, 2 * 1024 * 1024)
                        if (isReadableContent(response.contentType)) {
                            val source = response.bytes.toString(response.charset())
                            val readable = if (looksLikeHtml(response.contentType, source)) stripHtml(source) else source
                            readable.limitForTool(pageChars)
                        } else {
                            "Unsupported content type: ${response.contentType}"
                        }
                    }.getOrElse { "Fetch failed: ${it.message ?: it::class.java.simpleName}" }
                    appendLine(excerpt)
                }
                appendLine()
            }
        }.limitForTool(context.maxOutputSize.toInt())

        McpToolResult.success(text, mapOf("query" to query, "sources" to results.size, "pagesFetched" to fetchPages))
    }
}

private data class HttpResponse(
    val finalUrl: String,
    val contentType: String,
    val headers: Map<String, List<String>>,
    val bytes: ByteArray,
) {
    fun charset(): Charset {
        val charset = contentType.substringAfter("charset=", "").substringBefore(';').trim()
        return runCatching { if (charset.isNotBlank()) Charset.forName(charset) else Charsets.UTF_8 }.getOrDefault(Charsets.UTF_8)
    }

    fun fileNameFromHeaders(): String? {
        val value = headers.entries.firstOrNull { it.key.equals("Content-Disposition", ignoreCase = true) }
            ?.value?.firstOrNull().orEmpty()
        val match = Regex("filename\\*?=(?:UTF-8''|\")?([^\";]+)").find(value) ?: return null
        return URLDecoder.decode(match.groupValues[1].trim(), "UTF-8").sanitizeFileName()
    }
}

private data class SearchResult(val rank: Int, val title: String, val url: String, val snippet: String)

private fun fetchBytes(url: URL, timeoutSec: Int, maxBytes: Int, redirects: Int = 0): HttpResponse {
    if (redirects > 5) throw ToolError.InvalidParam("url", "too many redirects")
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = timeoutSec.coerceIn(1, 120) * 1000
    conn.readTimeout = timeoutSec.coerceIn(1, 120) * 1000
    conn.instanceFollowRedirects = false
    conn.setRequestProperty("User-Agent", "Xed-Editor/2.1 (+MCP Web Tools)")
    conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml,text/plain,application/json,*/*;q=0.8")

    val responseCode = conn.responseCode
    if (responseCode in 300..399) {
        val redirectUrl = conn.getHeaderField("Location") ?: throw ToolError.InvalidParam("url", "redirect with no Location header")
        val resolved = url.toURI().resolve(redirectUrl).toString()
        val safeRedirect = validateUrl(resolved) ?: throw ToolError.InvalidParam("url", "unsafe redirect: $resolved")
        return fetchBytes(safeRedirect, timeoutSec, maxBytes, redirects + 1)
    }
    if (responseCode !in 200..299) throw ToolError.InvalidParam("url", "HTTP $responseCode")

    conn.inputStream.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) throw ToolError.InvalidParam("maxBytes", "response exceeded $maxBytes bytes")
            out.write(buffer, 0, read)
        }
        return HttpResponse(
            finalUrl = conn.url.toString(),
            contentType = conn.contentType.orEmpty(),
            headers = conn.headerFields.entries.mapNotNull { (key, value) -> key?.let { it to value } }.toMap(),
            bytes = out.toByteArray()
        )
    }
}

private fun searchDuckDuckGo(query: String, num: Int): List<SearchResult> {
    return try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = validateUrl("https://html.duckduckgo.com/html/?q=$encodedQuery") ?: return emptyList()
        val html = fetchBytes(url, timeoutSec = 20, maxBytes = 2 * 1024 * 1024).bytes.toString(Charsets.UTF_8)
        val titleRegex = Regex("<a[^>]*class=\"result__a\"[^>]*href=\"(.*?)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val snippetRegex = Regex("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val titles = titleRegex.findAll(html).toList()
        val snippets = snippetRegex.findAll(html).toList()
        titles.take(num).mapIndexedNotNull { index, titleMatch ->
            val rawHref = htmlDecode(titleMatch.groupValues[1].trim())
            val href = normalizeSearchUrl(rawHref) ?: return@mapIndexedNotNull null
            val title = stripHtml(htmlDecode(titleMatch.groupValues[2])).trim()
            val snippet = snippets.getOrNull(index)?.groupValues?.getOrNull(1)?.let { stripHtml(htmlDecode(it)).trim() }.orEmpty()
            SearchResult(index + 1, title, href, snippet)
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun normalizeSearchUrl(rawHref: String): String? {
    val href = when {
        rawHref.startsWith("//") -> "https:$rawHref"
        rawHref.startsWith("/") -> "https://duckduckgo.com$rawHref"
        else -> rawHref
    }
    val uri = runCatching { URI(href) }.getOrNull() ?: return null
    val query = uri.rawQuery.orEmpty()
    val uddg = query.split('&').firstOrNull { it.startsWith("uddg=") }?.substringAfter('=')
    val decoded = if (uddg != null) URLDecoder.decode(uddg, "UTF-8") else href
    return validateUrl(decoded)?.toString()
}

private fun validateUrl(urlStr: String): URL? {
    return try {
        val url = URI(urlStr).normalize().toURL()
        val protocol = url.protocol.lowercase()
        if (protocol != "http" && protocol != "https") return null
        val host = url.host?.lowercase().orEmpty()
        if (host.isBlank() || host == "localhost") return null
        val addresses = InetAddress.getAllByName(host)
        if (addresses.isEmpty() || addresses.any { it.isUnsafeForFetch() }) return null
        url
    } catch (e: Exception) {
        null
    }
}

private fun InetAddress.isUnsafeForFetch(): Boolean {
    return isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress
}

private fun URL.fileNameFromPath(): String? {
    val pathName = path.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() } ?: return null
    return URLDecoder.decode(pathName, "UTF-8").sanitizeFileName()
}

private fun String.sanitizeFileName(): String = replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120).ifBlank { "download.bin" }

private fun isReadableContent(contentType: String): Boolean {
    val type = contentType.lowercase()
    return type.isBlank() || type.startsWith("text/") ||
        type.contains("html") || type.contains("json") || type.contains("xml") ||
        type.contains("javascript") || type.contains("x-www-form-urlencoded")
}

private fun looksLikeHtml(contentType: String, text: String): Boolean {
    val type = contentType.lowercase()
    return type.contains("html") || text.trimStart().startsWith("<!doctype", ignoreCase = true) || text.trimStart().startsWith("<html", ignoreCase = true)
}

private fun stripHtml(html: String): String {
    return html
        .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("<[^>]*>"), " ")
        .let { htmlDecode(it) }
        .replace(Regex("""[ \t\x0B\f\r]+"""), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun htmlToMarkdown(html: String, baseUrl: String): String {
    var md = html
    md = md.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
    md = md.replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
    md = md.replace(Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { "\n# ${stripHtml(it.groupValues[1])}\n" }
    md = md.replace(Regex("<h2[^>]*>(.*?)</h2>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { "\n## ${stripHtml(it.groupValues[1])}\n" }
    md = md.replace(Regex("<h3[^>]*>(.*?)</h3>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { "\n### ${stripHtml(it.groupValues[1])}\n" }
    md = md.replace(Regex("<pre[^>]*>(.*?)</pre>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { "\n```\n${stripHtml(it.groupValues[1])}\n```\n" }
    md = md.replace(Regex("<code[^>]*>(.*?)</code>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { "`${stripHtml(it.groupValues[1])}`" }
    md = md.replace(Regex("<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
        val href = runCatching { URI(baseUrl).resolve(htmlDecode(it.groupValues[1])).toString() }.getOrDefault(htmlDecode(it.groupValues[1]))
        "[${stripHtml(it.groupValues[2])}]($href)"
    }
    md = md.replace(Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { "\n- ${stripHtml(it.groupValues[1])}" }
    md = md.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    md = md.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
    return stripHtml(md).replace(Regex("\n{3,}"), "\n\n").trim()
}

private fun htmlDecode(text: String): String {
    return text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("&#(\\d+);")) { match -> match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value }
}

private fun String.limitForTool(maxChars: Int): String {
    return if (length > maxChars) take(maxChars) + "\n... (truncated at ${maxChars} chars)" else this
}
