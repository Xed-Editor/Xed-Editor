package com.rk.ai.nativeagent.tools

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

class VibeCodingGitHubTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(githubRepoInfo, githubReadme, githubFileFetch, githubSearchCode)

    private val githubRepoInfo = Tool(
        name = "github_repo_info",
        description = "Gets information about a GitHub repository (stars, forks, description, etc.).",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("repo", "Repository in format 'owner/repo' (e.g. 'anomalyco/opencode')")
                },
                required = listOf("repo"),
            )
        },
        execute = { args ->
            val repo = args.asJsonObject["repo"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing repo"))
            if (!repo.matches(Regex("^[\\w.-]+/[\\w.-]+$"))) return@Tool listOf(UIMessagePart.Text("Repo must be in 'owner/repo' format"))

            val json = githubApiGet("$GITHUB_API/repos/$repo")
            val data = JsonParser.parseString(json).asJsonObject

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
            listOf(UIMessagePart.Text(text.toString()))
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
            val repo = args.asJsonObject["repo"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing repo"))
            if (!repo.matches(Regex("^[\\w.-]+/[\\w.-]+$"))) return@Tool listOf(UIMessagePart.Text("Repo must be in 'owner/repo' format"))

            val json = githubApiGet("$GITHUB_API/repos/$repo/readme")
            val data = JsonParser.parseString(json).asJsonObject
            val content = data.get("content")?.asString?.replace("\n", "") ?: return@Tool listOf(UIMessagePart.Text("No README content found"))
            val decoded = java.util.Base64.getDecoder().decode(content).toString(Charsets.UTF_8)
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
            val branch = obj["branch"]?.asJsonPrimitive?.asString ?: ""

            val url = buildString {
                append("$GITHUB_API/repos/$repo/contents/$filePath")
                if (branch.isNotBlank()) append("?ref=$branch")
            }

            val json = githubApiGet(url)
            val data = JsonParser.parseString(json).asJsonObject
            val content = data.get("content")?.asString?.replace("\n", "") ?: return@Tool listOf(UIMessagePart.Text("Not a file or no content"))
            val decoded = java.util.Base64.getDecoder().decode(content).toString(Charsets.UTF_8)
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
            val repo = obj["repo"]?.asJsonPrimitive?.asString ?: ""

            val q = if (repo.isNotBlank()) "$query+repo:$repo" else query
            val url = "$GITHUB_API/search/code?q=${java.net.URLEncoder.encode(q, "UTF-8")}&per_page=$limit"
            val json = githubApiGet(url)
            val data = JsonParser.parseString(json).asJsonObject
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
            listOf(UIMessagePart.Text(text.toString()))
        },
    )

    private fun githubApiGet(urlStr: String): String {
        val url = URI(urlStr).toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "Xed-Editor/2.0")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

        val responseCode = conn.responseCode
        if (responseCode == 403) {
            val resetTime = conn.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
            val msg = if (resetTime != null) {
                val wait = resetTime * 1000 - System.currentTimeMillis()
                "GitHub API rate limited. Resets in ${wait / 1000}s"
            } else "GitHub API rate limited"
            throw RuntimeException(msg)
        }
        if (responseCode == 404) throw RuntimeException("Not found (404)")

        val reader = BufferedReader(InputStreamReader(
            if (responseCode in 200..299) conn.inputStream else conn.errorStream
        ))
        return reader.readText()
    }
}
