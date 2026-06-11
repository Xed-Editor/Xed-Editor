package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.File

class SemanticSearchTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Code Intelligence"
    override fun getName(): String = "semanticSearch"
    override fun getDescription(): String = """Performs semantic code search. Finds related files, understands code structure, and provides contextual results. Use for finding code by concept, not just text."""

    override fun getRequiredParams(): Map<String, String> = mapOf("query" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "type" to "string",
        "language" to "string",
        "maxResults" to "number",
        "includeRelated" to "boolean"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "query" to "Search concept or code pattern (e.g. 'authentication', 'database connection', 'error handling')"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "type" to "Search type: 'concept', 'pattern', 'structure', 'dependencies' (default: concept)",
        "language" to "Filter by language: 'kotlin', 'java', 'xml', etc.",
        "maxResults" to "Maximum results (default: 20)",
        "includeRelated" to "Include related files (default: true)"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val query = requireString(args, "query")
        val type = optionalString(args, "type", "concept")
        val language = optionalString(args, "language")
        val maxResults = (optionalInt(args, "maxResults") ?: 20).coerceIn(1, 100)
        val includeRelated = optionalBoolean(args, "includeRelated", true)

        val workspacePath = context.ideService.getPrimaryWorkspacePath()
        val root = File(workspacePath)
        if (!root.exists()) {
            return McpToolResult.error("Workspace not found: $workspacePath")
        }

        return when (type.lowercase()) {
            "concept" -> searchByConcept(root, query, language, maxResults, context)
            "pattern" -> searchByPattern(root, query, language, maxResults, context)
            "structure" -> searchByStructure(root, query, maxResults, context)
            "dependencies" -> searchByDependencies(root, query, maxResults, context)
            else -> McpToolResult.error("Unknown search type: $type. Use: concept, pattern, structure, dependencies")
        }
    }

    private suspend fun searchByConcept(
        root: File,
        query: String,
        language: String?,
        maxResults: Int,
        context: McpToolContext
    ): McpToolResult {
        val results = mutableListOf<SemanticResult>()
        val queryLower = query.lowercase()

        // Concept mappings for common code patterns
        val conceptPatterns = mapOf(
            "authentication" to listOf("auth", "login", "signin", "token", "credential", "password", "oauth", "jwt"),
            "database" to listOf("database", "db", "room", "dao", "entity", "migration", "sqlite", "repository"),
            "network" to listOf("http", "api", "retrofit", "okhttp", "ktor", "request", "response", "endpoint"),
            "ui" to listOf("compose", "view", "screen", "component", "widget", "layout", "dialog", "sheet"),
            "state" to listOf("state", "viewmodel", "livedata", "stateflow", "mutablestate", "observable"),
            "navigation" to listOf("nav", "route", "screen", "destination", "navigation", "backstack"),
            "error" to listOf("error", "exception", "catch", "throw", "error", "failure", "crash"),
            "test" to listOf("test", "spec", "mock", "stub", "assert", "verify", "junit"),
            "di" to listOf("inject", "module", "provide", "component", "hilt", "dagger", "koin"),
            "async" to listOf("coroutine", "suspend", "launch", "async", "flow", "channel", "thread"),
        )

        // Find matching concepts
        val matchedConcepts = mutableSetOf<String>()
        conceptPatterns.forEach { (concept, keywords) ->
            if (queryLower.contains(concept) || keywords.any { queryLower.contains(it) }) {
                matchedConcepts.add(concept)
            }
        }

        // If no specific concept matched, use the query as keywords
        val keywords = if (matchedConcepts.isEmpty()) {
            queryLower.split(" ", "_", "-").filter { it.length > 2 }
        } else {
            matchedConcepts.flatMap { conceptPatterns[it] ?: emptyList() }
        }

        // Search files
        val filesToSearch = root.walkTopDown()
            .maxDepth(8)
            .filter { file ->
                file.isFile && !file.name.startsWith(".") &&
                (language == null || file.extension == language) &&
                file.extension in listOf("kt", "java", "xml", "ts", "js", "py", "rs", "go", "swift")
            }
            .toList()

        filesToSearch.forEach { file ->
            try {
                if (file.length() > 500_000) return@forEach // Skip large files
                val content = file.readText()
                val contentLower = content.lowercase()
                val relativePath = file.relativeTo(root).path

                // Calculate relevance score
                var score = 0
                keywords.forEach { keyword ->
                    val occurrences = contentLower.split(keyword).size - 1
                    score += occurrences
                }

                // Boost score for file name matches
                if (keywords.any { file.name.lowercase().contains(it) }) {
                    score += 10
                }

                // Boost for package/class name matches
                val className = file.nameWithoutExtension
                if (keywords.any { className.lowercase().contains(it) }) {
                    score += 5
                }

                if (score > 0) {
                    // Find relevant snippets
                    val snippets = findRelevantSnippets(content, keywords, 3)

                    results.add(SemanticResult(
                        path = relativePath,
                        score = score,
                        snippets = snippets,
                        language = file.extension,
                        size = file.length()
                    ))
                }
            } catch (e: Exception) {
                // Skip files that can't be read
            }
        }

        // Sort by score and limit
        val sortedResults = results.sortedByDescending { it.score }.take(maxResults)

        if (sortedResults.isEmpty()) {
            return McpToolResult.success(
                buildString {
                    appendLine("## Semantic Search Results")
                    appendLine("**Query:** $query")
                    appendLine()
                    appendLine("No matches found. Try:")
                    appendLine("- Different keywords")
                    appendLine("- Broader search terms")
                    appendLine("- Check if the project has relevant code")
                },
                emptyMap()
            )
        }

        return McpToolResult.success(
            buildString {
                appendLine("## Semantic Search Results")
                appendLine("**Query:** $query")
                appendLine("**Matches:** ${sortedResults.size}")
                appendLine()
                sortedResults.forEach { result ->
                    appendLine("### ${result.path} (score: ${result.score}, ${result.language})")
                    result.snippets.forEach { snippet ->
                        appendLine("```")
                        appendLine(snippet)
                        appendLine("```")
                    }
                    appendLine()
                }
            },
            emptyMap()
        )
    }

    private suspend fun searchByPattern(
        root: File,
        pattern: String,
        language: String?,
        maxResults: Int,
        context: McpToolContext
    ): McpToolResult {
        val results = mutableListOf<PatternResult>()
        val patternLower = pattern.lowercase()

        // Common code patterns
        val codePatterns = mapOf(
            "class" to """(?:class|interface|enum|object)\s+(\w+)""".toRegex(),
            "function" to """(?:fun|function|def|func|fn)\s+(\w+)""".toRegex(),
            "variable" to """(?:val|var|const|let|const)\s+(\w+)""".toRegex(),
            "annotation" to """@\w+""".toRegex(),
            "import" to """(?:import|from|require)\s+([^;\n]+)""".toRegex(),
        )

        val filesToSearch = root.walkTopDown()
            .maxDepth(8)
            .filter { file ->
                file.isFile && !file.name.startsWith(".") &&
                (language == null || file.extension == language) &&
                file.extension in listOf("kt", "java", "ts", "js", "py", "rs")
            }
            .toList()

        filesToSearch.forEach { file ->
            try {
                if (file.length() > 500_000) return@forEach
                val lines = file.readLines()
                val relativePath = file.relativeTo(root).path

                lines.forEachIndexed { index, line ->
                    val lineLower = line.lowercase()
                    if (lineLower.contains(patternLower)) {
                        results.add(PatternResult(
                            path = relativePath,
                            line = index + 1,
                            content = line.trim(),
                            context = lines.drop(maxOf(0, index - 1)).take(3).joinToString("\n")
                        ))
                    }
                }
            } catch (e: Exception) {
                // Skip files that can't be read
            }
        }

        val sortedResults = results.take(maxResults)

        if (sortedResults.isEmpty()) {
            return McpToolResult.success("No pattern matches found for: $pattern", emptyMap())
        }

        return McpToolResult.success(
            buildString {
                appendLine("## Pattern Search Results")
                appendLine("**Pattern:** $pattern")
                appendLine("**Matches:** ${sortedResults.size}")
                appendLine()
                sortedResults.forEach { result ->
                    appendLine("**${result.path}:${result.line}**")
                    appendLine("```")
                    appendLine(result.context)
                    appendLine("```")
                    appendLine()
                }
            },
            emptyMap()
        )
    }

    private suspend fun searchByStructure(
        root: File,
        query: String,
        maxResults: Int,
        context: McpToolContext
    ): McpToolResult {
        val results = mutableListOf<StructureResult>()
        val queryLower = query.lowercase()

        // Analyze project structure
        val directories = root.walkTopDown()
            .maxDepth(6)
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .toList()

        directories.forEach { dir ->
            val relativePath = dir.relativeTo(root).path
            if (relativePath.lowercase().contains(queryLower) || dir.name.lowercase().contains(queryLower)) {
                val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
                val subdirs = dir.listFiles()?.filter { it.isDirectory } ?: emptyList()

                results.add(StructureResult(
                    path = relativePath,
                    type = "directory",
                    fileCount = files.size,
                    subdirectoryCount = subdirs.size,
                    files = files.take(10).map { it.name }
                ))
            }
        }

        val sortedResults = results.take(maxResults)

        if (sortedResults.isEmpty()) {
            return McpToolResult.success("No structural matches found for: $query", emptyMap())
        }

        return McpToolResult.success(
            buildString {
                appendLine("## Structure Search Results")
                appendLine("**Query:** $query")
                appendLine("**Matches:** ${sortedResults.size}")
                appendLine()
                sortedResults.forEach { result ->
                    appendLine("### ${result.path}")
                    appendLine("- Files: ${result.fileCount}")
                    appendLine("- Subdirectories: ${result.subdirectoryCount}")
                    if (result.files.isNotEmpty()) {
                        appendLine("- Sample files: ${result.files.joinToString(", ")}")
                    }
                    appendLine()
                }
            },
            emptyMap()
        )
    }

    private suspend fun searchByDependencies(
        root: File,
        query: String,
        maxResults: Int,
        context: McpToolContext
    ): McpToolResult {
        val results = mutableListOf<DependencyResult>()
        val queryLower = query.lowercase()

        // Search in build files for dependencies
        val buildFiles = root.walkTopDown()
            .maxDepth(4)
            .filter { it.isFile && (it.name == "build.gradle.kts" || it.name == "build.gradle" || it.name == "package.json") }
            .toList()

        buildFiles.forEach { file ->
            try {
                val content = file.readText()
                val relativePath = file.relativeTo(root).path

                // Find dependency lines containing the query
                val lines = content.lines()
                lines.forEachIndexed { index, line ->
                    if (line.lowercase().contains(queryLower)) {
                        results.add(DependencyResult(
                            file = relativePath,
                            line = index + 1,
                            content = line.trim(),
                            type = when {
                                file.name.contains("gradle") -> "gradle"
                                file.name.contains("package") -> "npm"
                                else -> "unknown"
                            }
                        ))
                    }
                }
            } catch (e: Exception) {
                // Skip files that can't be read
            }
        }

        val sortedResults = results.take(maxResults)

        if (sortedResults.isEmpty()) {
            return McpToolResult.success("No dependency matches found for: $query", emptyMap())
        }

        return McpToolResult.success(
            buildString {
                appendLine("## Dependency Search Results")
                appendLine("**Query:** $query")
                appendLine("**Matches:** ${sortedResults.size}")
                appendLine()
                sortedResults.forEach { result ->
                    appendLine("**${result.file}:${result.line}** (${result.type})")
                    appendLine("```")
                    appendLine(result.content)
                    appendLine("```")
                    appendLine()
                }
            },
            emptyMap()
        )
    }

    private fun findRelevantSnippets(content: String, keywords: List<String>, maxSnippets: Int): List<String> {
        val snippets = mutableListOf<String>()
        val lines = content.lines()

        for (i in lines.indices) {
            if (snippets.size >= maxSnippets) break
            val lineLower = lines[i].lowercase()
            if (keywords.any { lineLower.contains(it) }) {
                val start = maxOf(0, i - 1)
                val end = minOf(lines.size, i + 2)
                val snippet = lines.subList(start, end).joinToString("\n")
                if (snippet.isNotBlank()) {
                    snippets.add(snippet)
                }
            }
        }

        return snippets
    }
}

data class SemanticResult(
    val path: String,
    val score: Int,
    val snippets: List<String>,
    val language: String,
    val size: Long
)

data class PatternResult(
    val path: String,
    val line: Int,
    val content: String,
    val context: String
)

data class StructureResult(
    val path: String,
    val type: String,
    val fileCount: Int,
    val subdirectoryCount: Int,
    val files: List<String>
)

data class DependencyResult(
    val file: String,
    val line: Int,
    val content: String,
    val type: String
)
