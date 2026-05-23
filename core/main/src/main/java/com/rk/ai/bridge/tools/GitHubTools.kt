package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

abstract class BaseGitHubTool : BaseMcpTool() {
    override val timeoutMs: Long = 20_000L

    protected fun githubApiGet(path: String, token: String? = null): Result<JsonObject> = runCatching {
        val url = URL("https://api.github.com$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.setRequestProperty("User-Agent", "Xed-Editor-Bridge/1.0")
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "token $token")
        }

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val errorBody = try {
                BufferedReader(InputStreamReader(connection.errorStream, Charsets.UTF_8)).use { it.readText() }
            } catch (_: Exception) { "" }
            throw RuntimeException("GitHub API error $responseCode: ${connection.responseMessage}\n$errorBody")
        }

        val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
        val text = reader.use { it.readText() }
        connection.disconnect()
        JsonParser.parseString(text).asJsonObject
    }

    protected fun extractOwnerRepo(input: String): Pair<String, String> {
        val clean = input.trim().removePrefix("https://").removePrefix("http://")
            .removePrefix("github.com/").removePrefix("www.github.com/")
            .removeSuffix(".git").trimEnd('/')
        val parts = clean.split("/")
        return if (parts.size >= 2) parts[0] to parts[1] else "unknown" to parts.getOrElse(0) { "unknown" }
    }
}

class GitHubRepoInfoTool : BaseGitHubTool() {
    override val name: String = "github_repo_info"
    override val description: String = "Gets information about a GitHub repository: description, stars, language, topics, license."
    override val requiredParams: Map<String, String> = mapOf("repo" to "string")
    override val optionalParams: Map<String, String> = mapOf("token" to "string")

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val repo = requireString(args, "repo", maxLength = 200)
        val token = optionalString(args, "token")

        val (owner, name) = extractOwnerRepo(repo)
        context.pushProgress(0.3f, "Fetching $owner/$name...")

        val result = githubApiGet("/repos/$owner/$name", token.ifBlank { null })
        return result.fold(
            onSuccess = { json ->
                context.pushProgress(0.8f, "Formatting...")
                val output = buildString {
                    appendLine("Repository: $owner/$name")
                    appendLine("Description: ${json.get("description")?.asString ?: "N/A"}")
                    appendLine("Stars: ${json.get("stargazers_count")?.asInt ?: 0}")
                    appendLine("Forks: ${json.get("forks_count")?.asInt ?: 0}")
                    appendLine("Language: ${json.get("language")?.asString ?: "N/A"}")
                    appendLine("Topics: ${json.getAsJsonArray("topics")?.joinToString(", ") ?: "N/A"}")
                    appendLine("License: ${json.getAsJsonObject("license")?.get("spdx_id")?.asString ?: "N/A"}")
                    appendLine("Open Issues: ${json.get("open_issues_count")?.asInt ?: 0}")
                    appendLine("Default Branch: ${json.get("default_branch")?.asString ?: "main"}")
                    appendLine("Created: ${json.get("created_at")?.asString ?: "N/A"}")
                    appendLine("Updated: ${json.get("updated_at")?.asString ?: "N/A"}")
                    appendLine("URL: https://github.com/$owner/$name")
                }
                context.pushProgress(1f, "Done")
                resultText(enforceOutputLimit(output))
            },
            onFailure = { e ->
                McpToolResult.error("Failed to fetch repo info: ${e.message}", -32000)
            }
        )
    }
}

class GitHubReadmeTool : BaseGitHubTool() {
    override val name: String = "github_readme"
    override val description: String = "Fetches the README content of a GitHub repository."
    override val requiredParams: Map<String, String> = mapOf("repo" to "string")
    override val optionalParams: Map<String, String> = mapOf("branch" to "string")

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val repo = requireString(args, "repo", maxLength = 200)
        val branch = optionalString(args, "branch", "main")

        val (owner, name) = extractOwnerRepo(repo)
        context.pushProgress(0.3f, "Fetching README for $owner/$name...")

        return runCatching {
            val url = URL("https://raw.githubusercontent.com/$owner/$name/$branch/README.md")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                // Try README.rst
                val url2 = URL("https://raw.githubusercontent.com/$owner/$name/$branch/README.rst")
                val conn2 = url2.openConnection() as HttpURLConnection
                conn2.connectTimeout = 10_000
                conn2.readTimeout = 10_000
                if (conn2.responseCode != 200) {
                    return@runCatching "README not found for $owner/$name"
                }
                val reader = BufferedReader(InputStreamReader(conn2.inputStream, Charsets.UTF_8))
                return@runCatching reader.use { it.readText() }
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            val text = reader.use { it.readText() }
            connection.disconnect()
            text
        }.let { content ->
            context.pushProgress(1f, "Done")
            resultText(enforceOutputLimit(
                content.getOrElse { "Failed: ${content.exceptionOrNull()?.message}" }
            ))
        }
    }
}

class GitHubFileFetchTool : BaseGitHubTool() {
    override val name: String = "github_file_fetch"
    override val description: String = "Fetches a specific file from a GitHub repository."
    override val requiredParams: Map<String, String> = mapOf("repo" to "string", "path" to "string")
    override val optionalParams: Map<String, String> = mapOf("branch" to "string")

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val repo = requireString(args, "repo", maxLength = 200)
        val filePath = requireString(args, "path", maxLength = 500)
        val branch = optionalString(args, "branch", "main")

        val (owner, name) = extractOwnerRepo(repo)
        context.pushProgress(0.3f, "Fetching $filePath from $owner/$name...")

        return runCatching {
            val url = URL("https://raw.githubusercontent.com/$owner/$name/$branch/$filePath")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                return@runCatching "File not found (HTTP $responseCode)"
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            val text = reader.use { it.readText() }
            connection.disconnect()
            text
        }.let { content ->
            context.pushProgress(1f, "Done")
            resultText(enforceOutputLimit(
                content.getOrElse { "Failed: ${content.exceptionOrNull()?.message}" }
            ))
        }
    }
}

class GitHubSearchCodeTool : BaseGitHubTool() {
    override val name: String = "github_search_code"
    override val description: String = "Searches code on GitHub using the GitHub Code Search API."
    override val requiredParams: Map<String, String> = mapOf("query" to "string")
    override val optionalParams: Map<String, String> = mapOf(
        "repo" to "string",
        "language" to "string",
        "limit" to "number",
        "token" to "string",
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        var query = requireString(args, "query", maxLength = 500)
        val repo = optionalString(args, "repo")
        val language = optionalString(args, "language")
        val limit = optionalInt(args, "limit", 10).coerceIn(1, 50)
        val token = optionalString(args, "token")

        // Build GitHub search query
        if (repo.isNotBlank()) {
            val (owner, name) = extractOwnerRepo(repo)
            query += " repo:$owner/$name"
        }
        if (language.isNotBlank()) {
            query += " language:$language"
        }

        context.pushProgress(0.3f, "Searching GitHub code...")

        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val result = githubApiGet("/search/code?q=$encodedQuery&per_page=$limit&page=1", token.ifBlank { null })

        return result.fold(
            onSuccess = { json ->
                context.pushProgress(0.8f, "Formatting...")
                val totalCount = json.get("total_count")?.asInt ?: 0
                val items = json.getAsJsonArray("items") ?: com.google.gson.JsonArray()

                val output = buildString {
                    appendLine("GitHub Code Search: $query")
                    appendLine("Total results: $totalCount")
                    appendLine("Showing: ${items.size()} results")
                    appendLine()

                    items.forEach { item ->
                        val obj = item.asJsonObject
                        val name = obj.get("name")?.asString ?: "?"
                        val path = obj.get("path")?.asString ?: "?"
                        val repoFull = obj.getAsJsonObject("repository")?.get("full_name")?.asString ?: "?"
                        val htmlUrl = obj.get("html_url")?.asString ?: ""
                        appendLine("File: $name")
                        appendLine("Path: $path")
                        appendLine("Repo: $repoFull")
                        if (htmlUrl.isNotBlank()) appendLine("URL: $htmlUrl")
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

class GitHubIssueSearchTool : BaseGitHubTool() {
    override val name: String = "github_issue_search"
    override val description: String = "Searches issues on GitHub by query."
    override val requiredParams: Map<String, String> = mapOf("query" to "string")
    override val optionalParams: Map<String, String> = mapOf(
        "repo" to "string",
        "state" to "string",
        "limit" to "number",
        "token" to "string",
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        var query = requireString(args, "query", maxLength = 500)
        val repo = optionalString(args, "repo")
        val state = optionalString(args, "state", "open").takeIf { it in listOf("open", "closed", "all") } ?: "open"
        val limit = optionalInt(args, "limit", 10).coerceIn(1, 30)
        val token = optionalString(args, "token")

        if (repo.isNotBlank()) {
            val (owner, name) = extractOwnerRepo(repo)
            query += " repo:$owner/$name"
        }
        query += " state:$state"

        context.pushProgress(0.3f, "Searching issues...")

        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val result = githubApiGet("/search/issues?q=$encodedQuery&per_page=$limit&sort=updated&order=desc", token.ifBlank { null })

        return result.fold(
            onSuccess = { json ->
                context.pushProgress(0.8f, "Formatting...")
                val totalCount = json.get("total_count")?.asInt ?: 0
                val items = json.getAsJsonArray("items") ?: com.google.gson.JsonArray()

                val output = buildString {
                    appendLine("GitHub Issue Search: $query")
                    appendLine("Total results: $totalCount")
                    appendLine("Showing: ${items.size()} results")
                    appendLine()

                    items.forEach { item ->
                        val obj = item.asJsonObject
                        val number = obj.get("number")?.asInt ?: 0
                        val title = obj.get("title")?.asString ?: "?"
                        val state = obj.get("state")?.asString ?: "?"
                        val user = obj.getAsJsonObject("user")?.get("login")?.asString ?: "?"
                        val comments = obj.get("comments")?.asInt ?: 0
                        val htmlUrl = obj.get("html_url")?.asString ?: ""
                        val createdAt = obj.get("created_at")?.asString?.take(10) ?: "?"
                        val labels = obj.getAsJsonArray("labels")?.joinToString(", ") {
                            it.asJsonObject.get("name")?.asString ?: ""
                        } ?: ""

                        appendLine("#$number: $title ($state)")
                        appendLine("   Author: $user | Created: $createdAt | Comments: $comments")
                        if (labels.isNotBlank()) appendLine("   Labels: $labels")
                        if (htmlUrl.isNotBlank()) appendLine("   URL: $htmlUrl")
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
