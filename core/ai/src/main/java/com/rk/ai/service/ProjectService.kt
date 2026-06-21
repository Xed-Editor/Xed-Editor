package com.rk.ai.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.ai.AiConfig
import com.rk.ai.IdeBridge
import com.rk.ai.displayRootFor
import com.rk.ai.ideWorkspacePath
import com.rk.ai.resolveRelativePathFromOpenEditor
import com.rk.ai.resolveWorkspacePath
import com.rk.ai.workspaceRoots
import com.rk.filetree.FileTreeTab
import com.rk.search.SearchViewModel
import com.rk.settings.Settings
import com.rk.file.toFileWrapper
import com.rk.tabs.editor.EditorTab
import com.rk.utils.application
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

class ProjectService(private val tabRepo: TabRepository, private val viewModel: MainViewModel? = null) {

    private var cachedSearchVm: SearchViewModel? = null

    private fun searchViewModel(): SearchViewModel {
        val vm = cachedSearchVm
        if (vm != null) return vm
        return SearchViewModel().also { cachedSearchVm = it }
    }

    fun getPrimaryWorkspacePath(): String = IdeBridge.primaryWorkspacePath()

    suspend fun searchCode(query: String, limit: Int, path: String? = null, isRegex: Boolean = false): JsonArray {
        val app = application ?: return JsonArray()
        val vm = searchViewModel()
        val mv = viewModel ?: return JsonArray()
        
        val root = if (path != null) {
            resolvePath(path) ?: File(IdeBridge.primaryWorkspacePath())
        } else {
            File(IdeBridge.primaryWorkspacePath())
        }

        val results = withContext(Dispatchers.IO) {
            vm.searchCode(
                context = app, mainViewModel = mv,
                projectRoot = root.toFileWrapper(),
                query = query, useIndex = Settings.always_index_projects && path == null && !isRegex,
                isRegex = isRegex
            ).toList()
        }
        return JsonArray().apply {
            results.take(limit).forEach { item ->
                add(JsonObject().apply {
                    addProperty("path", item.file.getAbsolutePath())
                    addProperty("line", item.line + 1)
                    addProperty("column", item.column + 1)
                    addProperty("snippet", item.snippet.text.toString())
                })
            }
        }
    }

    suspend fun searchSymbols(query: String, limit: Int, path: String? = null): JsonArray {
        val escapedQuery = Regex.escape(query)
        val declarationPattern = Regex("\\b(class|interface|object|fun|def|function|var|val|let|const|enum|struct|type)\\s+$escapedQuery\\b", RegexOption.IGNORE_CASE)
        val allResults = searchCode(query, limit * 5, path = path, isRegex = false)
        return JsonArray().apply {
            var count = 0
            allResults.forEach { element ->
                if (count >= limit) return@forEach
                val obj = element.asJsonObject
                val snippet = obj.get("snippet").asString
                if (declarationPattern.containsMatchIn(snippet)) {
                    add(obj)
                    count++
                }
            }
            if (count == 0) {
                allResults.take(limit).forEach { add(it) }
            }
        }
    }

    suspend fun findFiles(query: String, limit: Int, path: String? = null): JsonArray {
        val root = if (path != null) {
            resolvePath(path) ?: File(IdeBridge.primaryWorkspacePath())
        } else {
            File(IdeBridge.primaryWorkspacePath())
        }
        if (!root.exists() || !root.isDirectory) return JsonArray()

        val isGlob = query.any { it == '*' || it == '?' || it == '[' }

        if (isGlob) {
            val results = JsonArray()
            val ignored = AiConfig.ignoredDirectories
            withContext(Dispatchers.IO) {
                val regex = globToRegex(query)
                val pattern = Regex(regex, RegexOption.IGNORE_CASE)
                root.walkTopDown()
                    .onEnter { it.name !in ignored }
                    .filter { it.isFile }
                    .take(limit * 2)
                    .filter { file ->
                        val relative = file.toRelativeString(root).let { if (it.startsWith("/")) it else "/$it" }
                        pattern.matches(relative) || pattern.matches(file.name)
                    }
                    .take(limit)
                    .forEach { file ->
                        results.add(JsonObject().apply {
                            addProperty("path", file.absolutePath)
                            addProperty("name", file.name)
                        })
                    }
            }
            return results
        }

        val app = application ?: return JsonArray()
        val vm = searchViewModel()
        val results = withContext(Dispatchers.IO) {
            vm.searchFileName(
                context = app,
                projectRoot = root.toFileWrapper(),
                query = query, useIndex = Settings.always_index_projects
            ).toList()
        }
        return JsonArray().apply {
            results.take(limit).forEach { meta ->
                add(JsonObject().apply {
                    addProperty("path", meta.path)
                    addProperty("name", meta.fileName)
                })
            }
        }
    }

    private fun globToRegex(glob: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when (c) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        if (i + 2 < glob.length && glob[i + 2] == '/') {
                            sb.append("(?:.+/)?")
                            i += 2
                        } else {
                            sb.append(".*")
                            i++
                        }
                    } else {
                        sb.append("[^/]*")
                    }
                }
                '?' -> sb.append("[^/]")
                '.' -> sb.append("\\.")
                '/' -> sb.append("/")
                '[' -> {
                    val close = glob.indexOf(']', i)
                    if (close == -1) {
                        sb.append(Regex.escape(c.toString()))
                    } else {
                        sb.append(glob.substring(i, close + 1))
                        i = close
                    }
                }
                else -> sb.append(Regex.escape(c.toString()))
            }
            i++
        }
        return "^$sb$"
    }

    private data class StructureCache(val path: String, val depth: Int, val items: Int, val result: String, val timestamp: Long)
    private var lastStructure: StructureCache? = null

    suspend fun getProjectStructure(path: String, maxDepth: Int, maxItems: Int): String {
        val now = System.currentTimeMillis()
        lastStructure?.let {
            if (it.path == path && it.depth == maxDepth && it.items == maxItems && now - it.timestamp < 3000) {
                return it.result
            }
        }

        val effectivePath = if (path.isBlank()) getPrimaryWorkspacePath() else path
        if (effectivePath.isBlank()) return "[!] No workspace configured. Open a project from the file tree first."

        val dir = resolvePath(effectivePath) ?: return "[!] Path outside workspace: $path"
        if (!dir.exists() || !dir.isDirectory) return "[!] Directory not found: $effectivePath"
        val ignored = AiConfig.ignoredDirectories
        val output = StringBuilder(4096)
        var count = 0
        withContext(Dispatchers.IO) {
            output.appendLine("[D] ${dir.name}/")
            dir.walkTopDown()
                .maxDepth(maxDepth)
                .onEnter { it.name !in ignored && !it.isHidden }
                .filter { it != dir }
                .take(maxItems)
                .forEach { child ->
                    val depth = child.toRelativeString(dir).split(File.separator).size
                    val indent = "  ".repeat(depth.coerceAtMost(maxDepth))
                    output.appendLine("$indent${if (child.isDirectory) "[D]" else "[F]"} ${child.name}")
                    count++
                }
            if (count >= maxItems) output.appendLine("  ... (truncated at $maxItems items)")
        }
        val result = output.toString()
        lastStructure = StructureCache(path, maxDepth, maxItems, result, now)
        return result
    }

    suspend fun getProjectConfig(workspacePath: String): JsonObject {
        val root = runCatching {
            if (workspacePath.isNotBlank()) File(workspacePath) else File(getPrimaryWorkspacePath())
        }.getOrNull() ?: return JsonObject().apply { addProperty("error", "invalid workspace") }

        if (!root.exists() || !root.isDirectory) return JsonObject().apply { addProperty("error", "invalid workspace") }

        return withContext(Dispatchers.IO) {
            val result = JsonObject()
            val files = root.listFiles()?.map { it.name }?.toSet() ?: emptySet()
            val detected = AiConfig.ProjectDetection.configFiles.entries.firstOrNull { (file, _) -> file in files }
            if (detected != null) {
                val (_, info) = detected
                result.addProperty("language", info.first)
                result.addProperty("buildSystem", info.second)
                if (detected.key == "package.json") {
                    runCatching {
                        com.google.gson.JsonParser.parseString(File(root, "package.json").readText()).asJsonObject
                            .getAsJsonObject("scripts")?.let { result.add("scripts", it) }
                    }
                }
            } else if (AiConfig.ProjectDetection.pythonIndicators.any { it in files }) {
                result.addProperty("language", "Python")
                result.addProperty("buildSystem", "pip/poetry")
            } else {
                result.addProperty("language", "unknown")
                result.addProperty("buildSystem", "unknown")
            }
            result.addProperty("workspace", root.absolutePath)
            result.add("files", JsonArray().apply { files.filter { !it.startsWith(".") }.take(50).forEach { add(it) } })
            result
        }
    }

    suspend fun getSymbolUnderCursor(): JsonObject {
        val tab = withContext(Dispatchers.Main) { tabRepo.currentTab as? EditorTab } ?: return JsonObject()
        val editor = withContext(Dispatchers.Main) { tab.editorState.editor.get() } ?: return JsonObject()
        return withContext(Dispatchers.Main) {
            val line = editor.cursor.leftLine; val column = editor.cursor.leftColumn
            val text = editor.text.toString(); val lines = text.split("\n")
            val currentLine = lines.getOrNull(line) ?: ""
            val contextStart = (line - 5).coerceAtLeast(0); val contextEnd = (line + 5).coerceAtMost(lines.size - 1)
            val scopeLines = mutableListOf<String>()
            for (i in line downTo 0) { val l = lines.getOrNull(i) ?: break; scopeLines.add(l.trim()); if (l.contains("fun ") || l.contains("class ") || l.contains("def ") || l.contains("function ")) break }
            val selected = editor.getSelectedText().orEmpty()
            JsonObject().apply {
                addProperty("filePath", tab.file.getAbsolutePath()); addProperty("line", line + 1); addProperty("column", column + 1)
                addProperty("currentLine", currentLine); addProperty("selectedText", selected.take(1024))
                addProperty("context", lines.subList(contextStart, contextEnd + 1).joinToString("\n"))
                addProperty("enclosingScope", scopeLines.reversed().joinToString("\n").take(1024))
            }
        }
    }

    private fun resolvePath(path: String): File? {
        val normalized = path.trim()
        if (normalized.isBlank()) {
            val primary = getPrimaryWorkspacePath()
            if (primary.isNotBlank()) return File(primary)
            return null
        }
        if (!normalized.startsWith("file:") && !File(normalized).isAbsolute) {
            resolveRelativePathFromOpenEditor(normalized, tabRepo)?.let { return it }
        }
        val workspaceStr = IdeBridge.workspacePathForResolution()
        if (workspaceStr.isBlank() && File(normalized).isAbsolute) {
            val f = File(normalized)
            if (f.exists()) return f.canonicalFile
        }
        return com.rk.ai.resolveWorkspacePath(workspaceStr, path)
    }
}
