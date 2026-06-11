package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class CodebaseIndexerTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Code Intelligence"
    override fun getName(): String = "indexCodebase"
    override fun getDescription(): String = """Builds and queries a searchable index of the codebase. Use 'build' to create an index, 'search' to query it, 'stats' for statistics, 'dependencies' for module analysis, 'architecture' for project overview."""

    override fun getRequiredParams(): Map<String, String> = mapOf("action" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "scope" to "string",
        "depth" to "number",
        "includeTests" to "boolean",
        "query" to "string",
        "filePattern" to "string"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "action" to "Action: 'build', 'search', 'stats', 'dependencies', 'architecture', 'keyFiles'"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "scope" to "Index scope: 'project', 'directory', 'file' (default: project)",
        "depth" to "Directory depth to index (default: 5)",
        "includeTests" to "Include test files (default: true)",
        "query" to "Search query for 'search' action",
        "filePattern" to "File pattern filter (e.g. '*.kt', '**/*.gradle.kts')"
    )

    companion object {
        private val indexCache = ConcurrentHashMap<String, CodebaseIndex>()
    }

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val action = requireString(args, "action")
        val workspacePath = context.ideService.getPrimaryWorkspacePath()

        return when (action.lowercase()) {
            "build" -> buildIndex(context, workspacePath, args)
            "search" -> searchIndex(context, workspacePath, args)
            "stats" -> getIndexStats(context, workspacePath)
            "dependencies" -> getDependencies(context, workspacePath)
            "architecture" -> getArchitectureOverview(context, workspacePath)
            "keyFiles" -> getKeyFiles(context, workspacePath)
            else -> McpToolResult.error("Unknown action: $action. Use: build, search, stats, dependencies, architecture, keyFiles")
        }
    }

    private suspend fun buildIndex(context: McpToolContext, workspacePath: String, args: JsonObject): McpToolResult {
        val depth = optionalInt(args, "depth") ?: 5
        val includeTests = optionalBoolean(args, "includeTests", true)

        val index = CodebaseIndex()
        val root = File(workspacePath)
        if (!root.exists() || !root.isDirectory) {
            return McpToolResult.error("Workspace path does not exist or is not a directory: $workspacePath")
        }

        index.projectRoot = workspacePath
        index.projectName = root.name

        // Scan project structure
        scanDirectory(root, root, index, depth, 0, includeTests)

        // Detect key files
        detectKeyFiles(root, index)

        // Analyze modules (Gradle multi-module)
        analyzeModules(root, index)

        // Cache the index
        indexCache[workspacePath] = index

        return McpToolResult.success(
            buildString {
                appendLine("## Codebase Index Built Successfully")
                appendLine("**Project:** ${index.projectName}")
                appendLine("**Root:** $workspacePath")
                appendLine("**Files indexed:** ${index.files.size}")
                appendLine("**Directories:** ${index.directories.size}")
                appendLine("**Modules:** ${index.modules.size}")
                appendLine("**Languages:** ${index.languageStats.keys.sorted().joinToString(", ") { "$it (${index.languageStats[it]})" }}")
                appendLine()
                appendLine("### Key Files Detected:")
                index.keyFiles.forEach { (category, files) ->
                    appendLine("- **$category:** ${files.joinToString(", ") { it.name }}")
                }
                appendLine()
                appendLine("### Usage:")
                appendLine("- `search` action: Query the index by text, symbol, or pattern")
                appendLine("- `stats` action: Get detailed statistics")
                appendLine("- `architecture` action: Get project architecture overview")
                appendLine("- `dependencies` action: Analyze module dependencies")
            },
            emptyMap()
        )
    }

    private fun scanDirectory(
        root: File,
        current: File,
        index: CodebaseIndex,
        maxDepth: Int,
        currentDepth: Int,
        includeTests: Boolean
    ) {
        if (currentDepth > maxDepth) return
        if (current.name.startsWith(".")) return
        if (current.name == "node_modules" || current.name == "build" || current.name == ".gradle") return

        if (current.isDirectory) {
            index.directories.add(current.relativeTo(root).path)
            current.listFiles()?.forEach { child ->
                scanDirectory(root, child, index, maxDepth, currentDepth + 1, includeTests)
            }
        } else if (current.isFile) {
            val relativePath = current.relativeTo(root).path
            val extension = current.extension.lowercase()
            val isTestFile = relativePath.contains("test") || relativePath.contains("Test")

            if (!includeTests && isTestFile) return

            val fileInfo = FileInfo(
                path = relativePath,
                name = current.name,
                extension = extension,
                size = current.length(),
                lastModified = current.lastModified(),
                isTestFile = isTestFile,
                lineCount = countLines(current)
            )
            index.files.add(fileInfo)
            index.languageStats[extension] = (index.languageStats[extension] ?: 0) + 1
        }
    }

    private fun detectKeyFiles(root: File, index: CodebaseIndex) {
        val keyFilePatterns = mapOf(
            "Build Configuration" to listOf(
                "build.gradle.kts", "build.gradle", "settings.gradle.kts", "settings.gradle",
                "package.json", "Cargo.toml", "CMakeLists.txt", "Makefile", "pom.xml"
            ),
            "Android Manifest" to listOf("AndroidManifest.xml"),
            "Version Control" to listOf(".gitignore", ".gitmodules"),
            "Documentation" to listOf("README.md", "CONTRIBUTING.md", "LICENSE", "CHANGELOG.md"),
            "CI/CD" to listOf(".github/workflows/*.yml", ".gitlab-ci.yml", "Jenkinsfile"),
            "IDE Configuration" to listOf(".editorconfig", ".vscode/settings.json", "*.iml"),
            "ProGuard" to listOf("proguard-rules.pro", "consumer-rules.pro"),
        )

        keyFilePatterns.forEach { (category, patterns) ->
            patterns.forEach { pattern ->
                if (pattern.contains("*")) {
                    // Glob pattern - search recursively
                    root.walkTopDown()
                        .maxDepth(4)
                        .filter { it.isFile && matchesGlob(it.name, pattern) }
                        .forEach { index.keyFiles.getOrPut(category) { mutableListOf() }.add(it) }
                } else {
                    // Exact filename - search recursively
                    root.walkTopDown()
                        .maxDepth(4)
                        .filter { it.isFile && it.name == pattern }
                        .forEach { index.keyFiles.getOrPut(category) { mutableListOf() }.add(it) }
                }
            }
        }
    }

    private fun matchesGlob(name: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
            .toRegex()
        return regex.matches(name)
    }

    private fun analyzeModules(root: File, index: CodebaseIndex) {
        // Detect Gradle modules
        val settingsGradle = File(root, "settings.gradle.kts").takeIf { it.exists() }
            ?: File(root, "settings.gradle").takeIf { it.exists() }

        if (settingsGradle != null) {
            val content = settingsGradle.readText()
            val modulePattern = """include\(["']([^"']+)["']\)""".toRegex()
            modulePattern.findAll(content).forEach { match ->
                val modulePath = match.groupValues[1].replace(":", "/")
                val moduleDir = File(root, modulePath)
                if (moduleDir.exists()) {
                    val moduleInfo = ModuleInfo(
                        name = match.groupValues[1],
                        path = modulePath,
                        hasBuildFile = File(moduleDir, "build.gradle.kts").exists() || File(moduleDir, "build.gradle").exists(),
                        sourceFiles = moduleDir.walkTopDown()
                            .maxDepth(6)
                            .filter { it.isFile && it.extension in listOf("kt", "java", "xml") }
                            .count()
                    )
                    index.modules.add(moduleInfo)
                }
            }
        }

        // Detect npm/pip/cargo modules
        val packageJson = File(root, "package.json")
        if (packageJson.exists()) {
            index.modules.add(ModuleInfo(
                name = root.name,
                path = ".",
                hasBuildFile = true,
                sourceFiles = index.files.size
            ))
        }
    }

    private fun countLines(file: File): Int {
        return try {
            if (file.length() > 1_000_000) return -1 // Skip large files
            file.readLines().size
        } catch (e: Exception) {
            0
        }
    }

    private suspend fun searchIndex(context: McpToolContext, workspacePath: String, args: JsonObject): McpToolResult {
        val query = optionalString(args, "query")
        val filePattern = optionalString(args, "filePattern")

        val index = indexCache[workspacePath] ?: run {
            // Auto-build index if not cached
            val buildResult = buildIndex(context, workspacePath, args)
            indexCache[workspacePath] ?: return buildResult
        }

        val results = mutableListOf<String>()

        if (query.isNotBlank()) {
            // Search in file names
            index.files.filter { it.name.contains(query, ignoreCase = true) }
                .take(20)
                .forEach { results.add("File: ${it.path} (${it.lineCount} lines)") }

            // Search in directory names
            index.directories.filter { it.contains(query, ignoreCase = true) }
                .take(10)
                .forEach { results.add("Directory: $it") }

            // Search in key files
            index.keyFiles.values.flatten()
                .filter { it.name.contains(query, ignoreCase = true) || it.absolutePath.contains(query, ignoreCase = true) }
                .take(10)
                .forEach { results.add("Key file: ${it.relativeTo(File(index.projectRoot)).path}") }
        }

        if (filePattern.isNotBlank()) {
            index.files.filter { matchesGlob(it.name, filePattern) }
                .take(30)
                .forEach { results.add("${it.path} (${it.lineCount} lines, ${formatSize(it.size)})") }
        }

        if (results.isEmpty()) {
            return McpToolResult.success("No results found. Try building the index first with action='build'.")
        }

        return McpToolResult.success(
            buildString {
                appendLine("## Search Results (${results.size} matches)")
                appendLine()
                results.forEach { appendLine("- $it") }
            },
            emptyMap()
        )
    }

    private suspend fun getIndexStats(context: McpToolContext, workspacePath: String): McpToolResult {
        val index = indexCache[workspacePath]

        if (index == null) {
            return McpToolResult.success(
                buildString {
                    appendLine("## No Index Available")
                    appendLine("Build an index first with action='build'")
                    appendLine()
                    appendLine("### Quick Project Info:")
                    val structure = context.ideService.getProjectStructure(workspacePath, 3, 200)
                    appendLine(structure.take(15000))
                },
                emptyMap()
            )
        }

        val totalLines = index.files.sumOf { if (it.lineCount > 0) it.lineCount.toLong() else 0L }
        val totalSize = index.files.sumOf { it.size }

        return McpToolResult.success(
            buildString {
                appendLine("## Codebase Statistics")
                appendLine("**Project:** ${index.projectName}")
                appendLine("**Root:** $workspacePath")
                appendLine()
                appendLine("### Overview:")
                appendLine("- **Total files:** ${index.files.size}")
                appendLine("- **Total directories:** ${index.directories.size}")
                appendLine("- **Total lines:** $totalLines")
                appendLine("- **Total size:** ${formatSize(totalSize)}")
                appendLine("- **Modules:** ${index.modules.size}")
                appendLine()
                appendLine("### Language Breakdown:")
                index.languageStats.entries
                    .sortedByDescending { it.value }
                    .forEach { (lang, count) ->
                        appendLine("- .$lang: $count files")
                    }
                appendLine()
                appendLine("### Largest Files (by line count):")
                index.files
                    .filter { it.lineCount > 0 }
                    .sortedByDescending { it.lineCount }
                    .take(10)
                    .forEach { appendLine("- ${it.path}: ${it.lineCount} lines") }
                appendLine()
                appendLine("### Modules:")
                index.modules.forEach { module ->
                    appendLine("- **${module.name}** (${module.path}): ${module.sourceFiles} source files")
                }
            },
            emptyMap()
        )
    }

    private suspend fun getDependencies(context: McpToolContext, workspacePath: String): McpToolResult {
        val index = indexCache[workspacePath]
        val root = File(workspacePath)

        return McpToolResult.success(
            buildString {
                appendLine("## Project Dependencies")
                appendLine("**Workspace:** $workspacePath")
                appendLine()

                // Android/Gradle dependencies
                val buildFiles = root.walkTopDown()
                    .maxDepth(3)
                    .filter { it.isFile && (it.name == "build.gradle.kts" || it.name == "build.gradle") }
                    .toList()

                if (buildFiles.isNotEmpty()) {
                    appendLine("### Gradle Build Files:")
                    buildFiles.forEach { file ->
                        val relativePath = file.relativeTo(root).path
                        val deps = extractGradleDependencies(file)
                        if (deps.isNotEmpty()) {
                            appendLine("- **$relativePath:**")
                            deps.take(20).forEach { dep -> appendLine("  - $dep") }
                            if (deps.size > 20) appendLine("  - ... and ${deps.size - 20} more")
                        }
                    }
                    appendLine()
                }

                // npm dependencies
                val packageJson = File(root, "package.json")
                if (packageJson.exists()) {
                    appendLine("### npm Dependencies:")
                    val deps = extractNpmDependencies(packageJson)
                    deps.take(30).forEach { (name, version) ->
                        appendLine("- $name: $version")
                    }
                    if (deps.size > 30) appendLine("- ... and ${deps.size - 30} more")
                    appendLine()
                }

                // Module dependencies from index
                if (index != null && index.modules.isNotEmpty()) {
                    appendLine("### Module Structure:")
                    index.modules.forEach { module ->
                        appendLine("- **${module.name}** → ${module.path}")
                    }
                }

                appendLine()
                appendLine("### Tip: Use `architecture` action for a high-level project overview")
            },
            emptyMap()
        )
    }

    private fun extractGradleDependencies(buildFile: File): List<String> {
        return try {
            val content = buildFile.readText()
            val deps = mutableListOf<String>()

            // implementation, api, compileOnly, etc.
            val depPattern = """(implementation|api|compileOnly|runtimeOnly|testImplementation|androidTestImplementation)\s*[(\[]\s*["']([^"']+)["']""".toRegex()
            depPattern.findAll(content).forEach { match ->
                deps.add("${match.groupValues[1]}: ${match.groupValues[2]}")
            }

            // version catalog references
            val catalogPattern = """(implementation|api)\s*\(\s*(libs\.[a-zA-Z0-9._]+)""".toRegex()
            catalogPattern.findAll(content).forEach { match ->
                deps.add("${match.groupValues[1]}: ${match.groupValues[2]}")
            }

            deps.distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractNpmDependencies(packageJson: File): List<Pair<String, String>> {
        return try {
            val content = packageJson.readText()
            val deps = mutableListOf<Pair<String, String>>()

            val depPattern = """"([^"]+)":\s*"([^"]+)"""".toRegex()
            depPattern.findAll(content).forEach { match ->
                val name = match.groupValues[1]
                val version = match.groupValues[2]
                if (!name.startsWith("@") || name.count { it == '/' } == 1) {
                    deps.add(name to version)
                }
            }

            deps.distinctBy { it.first }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getArchitectureOverview(context: McpToolContext, workspacePath: String): McpToolResult {
        val index = indexCache[workspacePath]
        val root = File(workspacePath)

        return McpToolResult.success(
            buildString {
                appendLine("## Project Architecture Overview")
                appendLine("**Project:** ${root.name}")
                appendLine("**Root:** $workspacePath")
                appendLine()

                // Project type detection
                appendLine("### Project Type:")
                when {
                    File(root, "build.gradle.kts").exists() || File(root, "build.gradle").exists() -> {
                        appendLine("- **Android/Gradle project**")
                        val settingsGradle = File(root, "settings.gradle.kts").takeIf { it.exists() }
                            ?: File(root, "settings.gradle").takeIf { it.exists() }
                        if (settingsGradle != null) {
                            appendLine("- Multi-module Gradle project")
                        }
                    }
                    File(root, "package.json").exists() -> appendLine("- **Node.js/npm project**")
                    File(root, "Cargo.toml").exists() -> appendLine("- **Rust project**")
                    File(root, "pom.xml").exists() -> appendLine("- **Java/Maven project**")
                    else -> appendLine("- **Unknown project type**")
                }
                appendLine()

                // Source code organization
                if (index != null) {
                    appendLine("### Source Code Organization:")
                    val srcDirs = index.directories.filter { it.endsWith("/src") || it.contains("/src/") }
                    if (srcDirs.isNotEmpty()) {
                        srcDirs.take(10).forEach { appendLine("- $it") }
                    }
                    appendLine()

                    appendLine("### Key Directories:")
                    val importantDirs = index.directories.filter { dir ->
                        dir.contains("main") || dir.contains("api") || dir.contains("core") ||
                        dir.contains("feature") || dir.contains("domain") || dir.contains("data")
                    }.take(15)
                    importantDirs.forEach { appendLine("- $it") }
                    appendLine()
                }

                // Technology stack
                appendLine("### Technology Stack:")
                val buildFiles = root.walkTopDown().maxDepth(3)
                    .filter { it.isFile && (it.name == "build.gradle.kts" || it.name == "build.gradle") }
                    .toList()
                buildFiles.forEach { file ->
                    val content = try { file.readText() } catch (e: Exception) { "" }
                    if (content.contains("jetpack")) appendLine("- Jetpack Compose")
                    if (content.contains("room")) appendLine("- Room Database")
                    if (content.contains("ktor")) appendLine("- Ktor")
                    if (content.contains("retrofit")) appendLine("- Retrofit")
                    if (content.contains("okhttp")) appendLine("- OkHttp")
                    if (content.contains("hilt") || content.contains("dagger")) appendLine("- Hilt/Dagger DI")
                    if (content.contains("navigation")) appendLine("- Navigation Component")
                }
                appendLine()

                // File statistics
                if (index != null) {
                    appendLine("### Statistics:")
                    appendLine("- Total files: ${index.files.size}")
                    appendLine("- Source files: ${index.files.count { it.extension in listOf("kt", "java", "ts", "js", "py", "rs") }}")
                    appendLine("- Test files: ${index.files.count { it.isTestFile }}")
                    appendLine("- Total lines: ${index.files.sumOf { if (it.lineCount > 0) it.lineCount.toLong() else 0L }}")
                }
            },
            emptyMap()
        )
    }

    private suspend fun getKeyFiles(context: McpToolContext, workspacePath: String): McpToolResult {
        val index = indexCache[workspacePath]

        if (index == null) {
            return McpToolResult.success("No index available. Build one first with action='build'.", emptyMap())
        }

        return McpToolResult.success(
            buildString {
                appendLine("## Key Project Files")
                appendLine()
                index.keyFiles.forEach { (category, files) ->
                    appendLine("### $category:")
                    files.forEach { file ->
                        val relativePath = file.relativeTo(File(workspacePath)).path
                        appendLine("- $relativePath")
                    }
                    appendLine()
                }

                appendLine("### Quick Access:")
                appendLine("- Use `readFile` to read any of these files")
                appendLine("- Use `search` to find specific content")
                appendLine("- Use `architecture` for project overview")
            },
            emptyMap()
        )
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}

data class CodebaseIndex(
    var projectRoot: String = "",
    var projectName: String = "",
    val files: MutableList<FileInfo> = mutableListOf(),
    val directories: MutableSet<String> = mutableSetOf(),
    val languageStats: MutableMap<String, Int> = mutableMapOf(),
    val keyFiles: MutableMap<String, MutableList<File>> = mutableMapOf(),
    val modules: MutableList<ModuleInfo> = mutableListOf()
)

data class FileInfo(
    val path: String,
    val name: String,
    val extension: String,
    val size: Long,
    val lastModified: Long,
    val isTestFile: Boolean,
    val lineCount: Int
)

data class ModuleInfo(
    val name: String,
    val path: String,
    val hasBuildFile: Boolean,
    val sourceFiles: Int
)
