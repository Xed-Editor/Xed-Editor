package com.rk.ai.nativeagent.tools

import com.google.gson.JsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService
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

class VibeCodingWebTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(webFetch, webSearch, webDownload, webResearch)

    private val webFetch = Tool(
        name = "web_fetch",
        description = "Fetches and extracts readable content from a URL. Supports text, HTML, JSON, XML, and markdown output.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("url", "The URL to fetch content from")
                    addProperty("format", "Response format: 'text', 'markdown', 'html', or 'raw' (default: text)")
                    add("timeout", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Timeout in seconds (default: 30)") })
                    add("maxBytes", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Maximum response bytes (default: 5MB)") })
                },
                required = listOf("url"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val urlStr = obj["url"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing url"))
            val format = obj["format"]?.asJsonPrimitive?.asString ?: "text"
            val timeout = obj["timeout"]?.asJsonPrimitive?.asLong ?: 30L
            val maxBytes = (obj["maxBytes"]?.asJsonPrimitive?.asInt ?: 5 * 1024 * 1024).coerceIn(1, 20 * 1024 * 1024)

            val safeUrl = validateUrl(urlStr) ?: return@Tool listOf(UIMessagePart.Text("Invalid or unsafe URL"))
            val response = fetchUrl(safeUrl, timeout, maxBytes)

            if (!isReadableContent(response.contentType) && format != "raw") {
                return@Tool listOf(UIMessagePart.Text(
                    "Binary or unsupported content type '${response.contentType}'. Use web_download to save this file."
                ))
            }

            val source = response.bytes.toString(response.charset())
            val text = when (format.lowercase()) {
                "html", "raw" -> source
                "markdown" -> htmlToMarkdown(source, response.finalUrl)
                else -> if (looksLikeHtml(response.contentType, source)) stripHtml(source) else source
            }
            listOf(UIMessagePart.Text(text.take(maxBytes)))
        },
    )

    private val webSearch = Tool(
        name = "web_search",
        description = "Searches the web using DuckDuckGo and returns titles, URLs, and snippets.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("query", "Search query")
                    add("numResults", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Number of results (default: 8, max: 20)") })
                },
                required = listOf("query"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val query = obj["query"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing query"))
            val numResults = (obj["numResults"]?.asJsonPrimitive?.asInt ?: 8).coerceIn(1, 20)

            val results = searchDuckDuckGo(query, numResults)
            val text = results.joinToString("\n\n") { "${it.rank}. ${it.title}\nURL: ${it.url}\n${it.snippet}" }
            listOf(UIMessagePart.Text(text.ifEmpty { "No results found for: $query" }))
        },
    )

    private val webDownload = Tool(
        name = "web_download",
        description = "Downloads a URL to a workspace file. Creates parent directories automatically and preserves binary content.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("url", "URL to download")
                    addProperty("outputPath", "Workspace file path or existing directory where the download should be saved")
                    add("timeout", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Timeout in seconds (default: 60)") })
                    add("maxBytes", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Maximum bytes to download (default: 100MB)") })
                    add("overwrite", JsonObject().apply { addProperty("type", "boolean"); addProperty("description", "Overwrite if file exists (default: false)") })
                },
                required = listOf("url", "outputPath"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val urlStr = obj["url"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing url"))
            val outputPath = obj["outputPath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing outputPath"))
            val timeout = obj["timeout"]?.asJsonPrimitive?.asLong ?: 60L
            val maxBytes = (obj["maxBytes"]?.asJsonPrimitive?.asInt ?: 100 * 1024 * 1024).coerceIn(1, 500 * 1024 * 1024)
            val overwrite = obj["overwrite"]?.asJsonPrimitive?.asBoolean ?: false

            val safeUrl = validateUrl(urlStr) ?: return@Tool listOf(UIMessagePart.Text("Invalid or unsafe URL"))
            val targetBase = ideService.resolvePath(outputPath) ?: return@Tool listOf(UIMessagePart.Text("Invalid output path: $outputPath"))

            val response = fetchUrl(safeUrl, timeout.coerceIn(1, 120), maxBytes)
            val target = if (targetBase.exists() && targetBase.isDirectory) {
                val fileName = response.fileNameFromPath() ?: "download.bin"
                File(targetBase, fileName)
            } else {
                targetBase
            }
            if (target.exists() && !overwrite) return@Tool listOf(UIMessagePart.Text("File already exists: ${target.absolutePath}"))
            target.parentFile?.mkdirs()
            target.writeBytes(response.bytes)
            listOf(UIMessagePart.Text("Downloaded ${response.bytes.size} bytes to ${target.absolutePath}"))
        },
    )

    private val webResearch = Tool(
        name = "web_research",
        description = "Searches the web and fetches top result pages for research. Returns sources plus readable excerpts.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("query", "Research query")
                    add("numResults", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Number of results to inspect (default: 5, max: 10)") })
                    add("fetchPages", JsonObject().apply { addProperty("type", "boolean"); addProperty("description", "Fetch readable excerpts from result pages (default: true)") })
                    add("pageChars", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Characters per fetched page (default: 4000)") })
                    add("timeout", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Timeout per page in seconds (default: 20)") })
                },
                required = listOf("query"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val query = obj["query"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing query"))
            val numResults = (obj["numResults"]?.asJsonPrimitive?.asInt ?: 5).coerceIn(1, 10)
            val fetchPages = obj["fetchPages"]?.asJsonPrimitive?.asBoolean ?: true
            val pageChars = (obj["pageChars"]?.asJsonPrimitive?.asInt ?: 4000).coerceIn(500, 20000)
            val timeoutSec = (obj["timeout"]?.asJsonPrimitive?.asLong ?: 20L).coerceIn(1, 60)

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
                            val safeUrl = validateUrl(result.url) ?: return@runCatching "Invalid URL"
                            val resp = fetchUrl(safeUrl, timeoutSec, 2 * 1024 * 1024)
                            if (isReadableContent(resp.contentType)) {
                                val src = resp.bytes.toString(resp.charset())
                                val readable = if (looksLikeHtml(resp.contentType, src)) stripHtml(src) else src
                                readable.take(pageChars)
                            } else {
                                "Unsupported content type: ${resp.contentType}"
                            }
                        }.getOrElse { "Fetch failed: ${it.message ?: it::class.java.simpleName}" }
                        appendLine(excerpt)
                    }
                    appendLine()
                }
            }
            listOf(UIMessagePart.Text(text))
        },
    )

    private data class FetchResponse(
        val finalUrl: String,
        val contentType: String,
        val bytes: ByteArray,
    ) {
        fun charset(): Charset {
            val charset = contentType.substringAfter("charset=", "").substringBefore(';').trim()
            return runCatching { if (charset.isNotBlank()) Charset.forName(charset) else Charsets.UTF_8 }.getOrDefault(Charsets.UTF_8)
        }
        fun fileNameFromPath(): String? {
            val path = finalUrl.substringAfter("://").substringAfter('/').substringBefore('?').substringAfterLast('/')
            return path.takeIf { it.isNotBlank() && !it.contains('.') }?.let { "download.bin" } ?: path.takeIf { it.isNotBlank() } ?: "download.bin"
        }
    }

    private data class SearchResult(val rank: Int, val title: String, val url: String, val snippet: String)

    private fun fetchUrl(url: URL, timeoutSec: Long, maxBytes: Int, redirects: Int = 0): FetchResponse {
        if (redirects > 5) throw RuntimeException("Too many redirects")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = (timeoutSec * 1000).toInt()
        conn.readTimeout = (timeoutSec * 1000).toInt()
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "Xed-Editor/2.1 (+VibeCoding Agent)")
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml,text/plain,application/json,*/*;q=0.8")

        val responseCode = conn.responseCode
        if (responseCode in 300..399) {
            val redirectUrl = conn.getHeaderField("Location") ?: throw RuntimeException("Redirect with no Location header")
            val resolved = url.toURI().resolve(redirectUrl).toString()
            val safeRedirect = validateUrl(resolved) ?: throw RuntimeException("Unsafe redirect: $resolved")
            return fetchUrl(safeRedirect, timeoutSec, maxBytes, redirects + 1)
        }
        if (responseCode !in 200..299) throw RuntimeException("HTTP $responseCode")

        conn.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) throw RuntimeException("Response exceeded $maxBytes bytes")
                out.write(buffer, 0, read)
            }
            return FetchResponse(
                finalUrl = conn.url.toString(),
                contentType = conn.contentType.orEmpty(),
                bytes = out.toByteArray(),
            )
        }
    }

    private fun searchDuckDuckGo(query: String, num: Int): List<SearchResult> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = validateUrl("https://html.duckduckgo.com/html/?q=$encodedQuery") ?: return emptyList()
            val html = fetchUrl(url, 20L, 2 * 1024 * 1024).bytes.toString(Charsets.UTF_8)
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
            if (host.isBlank()) return null
            val addresses = InetAddress.getAllByName(host)
            if (addresses.isEmpty() || addresses.any { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress }) return null
            url
        } catch (e: Exception) { null }
    }

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
}
