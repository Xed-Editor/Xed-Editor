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

private const val GITHUB_API = "https://api.github.com"
private const val USER_AGENT = "Xed-Editor/2.0"

class GitHubRepoInfoTool : BaseMcpTool() {
    override fun getName(): String = "github_repo_info"
    override fun getDescription(): String = "Gets information about a GitHub repository (stars, forks, description, etc.)."
    override fun getRequiredParams(): Map<String, String> = mapOf("repo" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "repo" to "Repository in format 'owner/repo' (e.g. 'anomalyco/opencode')"
    )
    override fun getTimeoutMs(): Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val repo = requireString(args, "repo")
        if (!repo.matches(Regex("^[\\w.-]+/[\\w.-]+$"))) throw ToolError.InvalidParam("repo", "must be in 'owner/repo' format")

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
        McpToolResult.success(text.toString())
    }
}

class GitHubReadmeTool : BaseMcpTool() {
    override fun getName(): String = "github_readme"
    override fun getDescription(): String = "Fetches the README content of a GitHub repository."
    override fun getRequiredParams(): Map<String, String> = mapOf("repo" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "repo" to "Repository in format 'owner/repo'"
    )
    override fun getTimeoutMs(): Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val repo = requireString(args, "repo")
        if (!repo.matches(Regex("^[\\w.-]+/[\\w.-]+$"))) throw ToolError.InvalidParam("repo", "must be in 'owner/repo' format")

        val json = githubApiGet("$GITHUB_API/repos/$repo/readme")
        val data = JsonParser.parseString(json).asJsonObject
        val content = data.get("content")?.asString?.replace("\n", "") ?: throw ToolError.InvalidParam("repo", "no readme content found")
        val decoded = java.util.Base64.getDecoder().decode(content).toString(Charsets.UTF_8)
        McpToolResult.success(decoded)
    }
}

class GitHubFileFetchTool : BaseMcpTool() {
    override fun getName(): String = "github_file_fetch"
    override fun getDescription(): String = "Fetches a specific file from a GitHub repository."
    override fun getRequiredParams(): Map<String, String> = mapOf("repo" to "string", "path" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("branch" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "repo" to "Repository in format 'owner/repo'",
        "path" to "File path within the repository"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "branch" to "Branch name (default: repository default branch)"
    )
    override fun getTimeoutMs(): Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val repo = requireString(args, "repo")
        val filePath = requireString(args, "path")
        val branch = optionalString(args, "branch")

        val url = buildString {
            append("$GITHUB_API/repos/$repo/contents/$filePath")
            if (branch.isNotBlank()) append("?ref=$branch")
        }

        val json = githubApiGet(url)
        val data = JsonParser.parseString(json).asJsonObject
        val content = data.get("content")?.asString?.replace("\n", "") ?: throw ToolError.InvalidParam("path", "not a file or no content")
        val decoded = java.util.Base64.getDecoder().decode(content).toString(Charsets.UTF_8)
        val size = data.get("size")?.asInt ?: decoded.length

        McpToolResult.success(decoded, mapOf(
            "path" to filePath,
            "size" to size.toString(),
            "repo" to repo
        ))
    }
}

class GitHubSearchCodeTool : BaseMcpTool() {
    override fun getName(): String = "github_search_code"
    override fun getDescription(): String = "Searches code on GitHub using the search API."
    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("limit" to "number", "repo" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "query" to "Search query"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "limit" to "Maximum results (default: 10)",
        "repo" to "Limit search to a specific repository (owner/repo)"
    )
    override fun getTimeoutMs(): Long = 30_000L

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(Dispatchers.IO) {
        val query = requireString(args, "query")
        val limit = (optionalPositiveInt(args, "limit") ?: 10).coerceIn(1, 50)
        val repo = optionalString(args, "repo")

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
                val obj = item.asJsonObject
                appendLine("File: ${obj.get("path")?.asString ?: "?"}")
                appendLine("Repo: ${obj.getAsJsonObject("repository")?.get("full_name")?.asString ?: "?"}")
                appendLine("URL: ${obj.get("html_url")?.asString ?: "?"}")
                appendLine()
            }
        }
        McpToolResult.success(text.toString(), mapOf("total" to (data.get("total_count")?.asInt ?: 0).toString()))
    }
}

private fun githubApiGet(urlStr: String): String {
    val url = URI(urlStr).toURL()
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 20_000
    conn.readTimeout = 20_000
    conn.setRequestProperty("User-Agent", USER_AGENT)
    conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

    val responseCode = conn.responseCode
    if (responseCode == 403) {
        val resetTime = conn.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
        val msg = if (resetTime != null) {
            val wait = resetTime * 1000 - System.currentTimeMillis()
            "GitHub API rate limited. Resets in ${wait / 1000}s"
        } else "GitHub API rate limited"
        throw ToolError.Internal(RuntimeException(msg))
    }
    if (responseCode == 404) throw ToolError.InvalidParam("repo", "not found (404)")

    val reader = BufferedReader(InputStreamReader(
        if (responseCode in 200..299) conn.inputStream else conn.errorStream
    ))
    return reader.readText()
}
