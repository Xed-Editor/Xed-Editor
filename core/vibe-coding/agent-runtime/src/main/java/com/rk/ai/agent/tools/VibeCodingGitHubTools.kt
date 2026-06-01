package com.rk.ai.agent.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI

private const val GITHUB_API = "https://api.github.com"
private const val GITHUB_API_TIMEOUT_MS = 20_000
private val REPO_FORMAT = Regex("^[\\w.-]+/[\\w.-]+\$")

class VibeCodingGitHubTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(githubRepoInfo, githubReadme, githubFileFetch, githubSearchCode)

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun validateRepo(repo: String): UIMessagePart.Text? =
        if (!repo.matches(REPO_FORMAT))
            UIMessagePart.Text("Repo must be in 'owner/repo' format (e.g. 'torvalds/linux')")
        else null

    /**
     * Decodes the Base64-encoded file content returned by the GitHub Contents API.
     * Returns null if the field is absent.
     */
    private fun JsonObject.decodeBase64Content(): String? {
        val raw = get("content")?.asString?.replace("\n", "") ?: return null
        return java.util.Base64.getDecoder().decode(raw).toString(Charsets.UTF_8)
    }

    /**
     * Performs a GET request against the GitHub API and returns the response body as a String.
     * Handles rate-limiting (403) and not-found (404) explicitly.
     * Closes the connection in all code paths.
     */
    private fun githubApiGet(urlStr: String): String {
        val conn = URI(urlStr).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = GITHUB_API_TIMEOUT_MS
            conn.readTimeout = GITHUB_API_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "Xed-Editor/2.0")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            val responseCode = conn.responseCode
            if (responseCode == 403) {
                val resetTime = conn.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
                val msg = if (resetTime != null) {
                    val waitSec = (resetTime * 1000 - System.currentTimeMillis()) / 1000
                    "GitHub API rate limited. Resets in ${waitSec}s"
                } else "GitHub API rate limited"
                throw RuntimeException(msg)
            }
            if (responseCode == 404) throw RuntimeException("Not found (404)")

            return BufferedReader(
                InputStreamReader(
                    if (responseCode in 200..299) conn.inputStream else conn.errorStream,
                )
            ).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ── Tool definitions ───────────────────────────────────────────────────

    private val githubRepoInfo = Tool(
        name = "github_repo_info",
        description = "Gets information about a GitHub repository (stars, forks, description, etc.).",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("repo", "Repository in format 'owner/repo' (e.g. 'torvalds/linux')")
                },
                required = listOf("repo"),
            )
        },
        execute = { args ->
            val repo = args.asJsonObject["repo"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing repo"))
            validateRepo(repo)?.let { return@Tool listOf(it) }

            val data = JsonParser.parseString(githubApiGet("$GITHUB_API/repos/$repo")).asJsonObject

            val text = buildString {
                appendLine("Repository: ${data.get("full_name")?.asString ?: repo}")
                appendLine("Description: ${data.get("description")?.asString ?: "N/A"}")
                appendLine("Stars: ${data.get("stargazers_count")?.asInt ?: 0}")
                appendLine("Forks: ${data.get("forks_count")?.asInt ?: 0}")
                appendLine("Open Issues: ${data.get("open_issues_count")?.asInt ?: 0}")
                appendLine("Language: ${data.get("language")?.asString ?: "N/A"}")
                appendLine("License: ${data.getAsJsonObject("license")?.get("spdx_id")?.asString ?: "N/A"}")
                appendLine("Topics: ${data.getAsJsonArray("topics")?.joinToString(", ") { it.asString } ?: "none"}")
                appendLine("URL: ${data.get("html_url")?.asString ?: ""}")
                appendLine("Default Branch: ${data.get("default_branch")?.asString ?: "main"}")
                val pushedAt = data.get("pushed_at")?.asString ?: ""
                if (pushedAt.isNotBlank()) appendLine("Last Push: $pushedAt")
            }
            listOf(UIMessagePart.Text(text))
        },
    )

    private val githubReadme = Tool(
        name = "github_readme",
        description = "Fetches the README content of a GitHub repository.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("repo", "Repository in format 'owner/repo'")
                },
                required = listOf("repo"),
            )
        },
        execute = { args ->
            val repo = args.asJsonObject["repo"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing repo"))
            validateRepo(repo)?.let { return@Tool listOf(it) }

            val data = JsonParser.parseString(githubApiGet("$GITHUB_API/repos/$repo/readme")).asJsonObject
            val decoded = data.decodeBase64Content()
                ?: return@Tool listOf(UIMessagePart.Text("No README content found"))
            listOf(UIMessagePart.Text(decoded))
        },
    )

    private val githubFileFetch = Tool(
        name = "github_file_fetch",
        description = "Fetches a specific file from a GitHub repository.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("repo", "Repository in format 'owner/repo'")
                    addProperty("path", "File path within the repository")
                    addProperty("branch", "Branch name (default: repository default branch)")
                },
                required = listOf("repo", "path"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val repo = obj["repo"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing repo"))
            val filePath = obj["path"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing path"))
            val branch = obj["branch"]?.asJsonPrimitive?.asString.orEmpty()

            val url = buildString {
                append("$GITHUB_API/repos/$repo/contents/$filePath")
                if (branch.isNotBlank()) append("?ref=$branch")
            }

            val data = JsonParser.parseString(githubApiGet(url)).asJsonObject
            val decoded = data.decodeBase64Content()
                ?: return@Tool listOf(UIMessagePart.Text("Not a file or no content"))
            listOf(UIMessagePart.Text(decoded))
        },
    )

    private val githubSearchCode = Tool(
        name = "github_search_code",
        description = "Searches code on GitHub using the search API.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("query", "Search query")
                    add("limit", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Maximum results (default: 10)") })
                    addProperty("repo", "Limit search to a specific repository (owner/repo)")
                },
                required = listOf("query"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val query = obj["query"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing query"))
            val limit = (obj["limit"]?.asJsonPrimitive?.asInt ?: 10).coerceIn(1, 50)
            val repo = obj["repo"]?.asJsonPrimitive?.asString.orEmpty()

            val q = if (repo.isNotBlank()) "$query+repo:$repo" else query
            val url = "$GITHUB_API/search/code?q=${java.net.URLEncoder.encode(q, "UTF-8")}&per_page=$limit"
            val data = JsonParser.parseString(githubApiGet(url)).asJsonObject
            val items = data.getAsJsonArray("items") ?: JsonArray()

            val text = buildString {
                val total = data.get("total_count")?.asInt ?: 0
                appendLine("Found $total results (showing ${items.size()})")
                appendLine()
                items.forEach { item ->
                    val itemObj = item.asJsonObject
                    appendLine("File: ${itemObj.get("path")?.asString ?: "?"}")
                    appendLine("Repo: ${itemObj.getAsJsonObject("repository")?.get("full_name")?.asString ?: "?"}")
                    appendLine("URL: ${itemObj.get("html_url")?.asString ?: "?"}")
                    appendLine()
                }
            }
            listOf(UIMessagePart.Text(text))
        },
    )
}
