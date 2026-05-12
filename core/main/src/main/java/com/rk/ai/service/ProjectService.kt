package com.rk.ai.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.ai.IdeBridge
import com.rk.file.FileWrapper
import com.rk.file.toFileWrapper
import com.rk.search.SearchViewModel
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.utils.application
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

class ProjectService(private val viewModel: MainViewModel) {

    fun getPrimaryWorkspacePath(): String = IdeBridge.primaryWorkspacePath()

    suspend fun searchCode(query: String, limit: Int): JsonArray {
        val results = JsonArray()
        val searchViewModel = SearchViewModel()
        val projectRoot = File(IdeBridge.primaryWorkspacePath()).toFileWrapper()
        withContext(Dispatchers.IO) {
            val app = application!!
            searchViewModel.searchCode(
                context = app, mainViewModel = viewModel, projectRoot = projectRoot,
                query = query, useIndex = Settings.always_index_projects
            ).take(limit).collect { item ->
                results.add(JsonObject().apply {
                    addProperty("path", item.file.getAbsolutePath())
                    addProperty("line", item.line + 1)
                    addProperty("column", item.column + 1)
                    addProperty("snippet", item.snippet.text.toString())
                })
            }
        }
        return results
    }

    suspend fun findFiles(query: String, limit: Int): JsonArray {
        val results = JsonArray()
        val searchViewModel = SearchViewModel()
        val projectRoot = File(IdeBridge.primaryWorkspacePath()).toFileWrapper()
        withContext(Dispatchers.IO) {
            val app = application!!
            searchViewModel.searchFileName(
                context = app, projectRoot = projectRoot,
                query = query, useIndex = Settings.always_index_projects
            ).take(limit).forEach { meta ->
                results.add(JsonObject().apply {
                    addProperty("path", meta.path)
                    addProperty("name", meta.fileName)
                })
            }
        }
        return results
    }

    suspend fun getProjectStructure(path: String, maxDepth: Int, maxItems: Int): String {
        val dir = resolvePath(path) ?: throw IllegalArgumentException("path outside workspace: $path")
        if (!dir.exists() || !dir.isDirectory) throw IllegalArgumentException("not a directory: $path")
        val ignored = setOf(".git", ".gradle", ".idea", "build", "node_modules", ".dex", ".cache")
        val output = StringBuilder()
        val count = intArrayOf(0)
        fun walk(current: File, depth: Int) {
            if (count[0] >= maxItems || depth > maxDepth) return
            val indent = "  ".repeat(depth)
            val children = current.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: return
            for (child in children) {
                if (count[0] >= maxItems) return
                if (child.name in ignored) continue
                if (child.isHidden && child.name != ".env") continue
                output.appendLine("$indent${if (child.isDirectory) "[D]" else "[F]"} ${child.name}")
                count[0]++
                if (child.isDirectory && depth < maxDepth) walk(child, depth + 1)
            }
        }
        withContext(Dispatchers.IO) {
            output.appendLine("[D] ${dir.name}/")
            walk(dir, 1)
            if (count[0] >= maxItems) output.appendLine("  ... (truncated at $maxItems items)")
        }
        return output.toString()
    }

    suspend fun getProjectConfig(workspacePath: String): JsonObject {
        val result = JsonObject()
        val root = if (workspacePath.isNotBlank()) File(workspacePath) else File(getPrimaryWorkspacePath())
        if (!root.exists() || !root.isDirectory) return result.apply { addProperty("error", "invalid workspace") }
        withContext(Dispatchers.IO) {
            val files = root.listFiles()?.map { it.name }?.toSet() ?: emptySet()
            when {
                "package.json" in files -> {
                    result.addProperty("language", "JavaScript/TypeScript"); result.addProperty("buildSystem", "npm/yarn/pnpm")
                    runCatching { com.google.gson.JsonParser.parseString(File(root, "package.json").readText()).asJsonObject.getAsJsonObject("scripts")?.let { result.add("scripts", it) } }
                }
                "pubspec.yaml" in files -> { result.addProperty("language", "Dart"); result.addProperty("buildSystem", "pub") }
                "build.gradle.kts" in files || "build.gradle" in files -> { result.addProperty("language", "Kotlin/Java"); result.addProperty("buildSystem", "Gradle") }
                "Cargo.toml" in files -> { result.addProperty("language", "Rust"); result.addProperty("buildSystem", "Cargo") }
                "CMakeLists.txt" in files -> { result.addProperty("language", "C/C++"); result.addProperty("buildSystem", "CMake") }
                "go.mod" in files -> { result.addProperty("language", "Go"); result.addProperty("buildSystem", "go mod") }
                "requirements.txt" in files || "setup.py" in files || "pyproject.toml" in files -> { result.addProperty("language", "Python"); result.addProperty("buildSystem", "pip/poetry") }
                else -> { result.addProperty("language", "unknown"); result.addProperty("buildSystem", "unknown") }
            }
            result.addProperty("workspace", root.absolutePath)
            result.add("files", JsonArray().apply { files.filter { !it.startsWith(".") }.take(50).forEach { add(it) } })
        }
        return result
    }

    suspend fun getSymbolUnderCursor(): JsonObject {
        val tab = withContext(Dispatchers.Main) { viewModel.currentTab as? EditorTab } ?: return JsonObject()
        val editor = withContext(Dispatchers.Main) { tab.editorState.editor.get() } ?: return JsonObject()
        return withContext(Dispatchers.Main) {
            val line = editor.cursor.leftLine; val column = editor.cursor.leftColumn
            val text = editor.text.toString(); val lines = text.split("\n")
            val currentLine = lines.getOrNull(line) ?: ""
            val contextStart = (line - 5).coerceAtLeast(0); val contextEnd = (line + 5).coerceAtMost(lines.size - 1)
            val contextLines = lines.subList(contextStart, contextEnd + 1)
            val scopeLines = mutableListOf<String>()
            for (i in line downTo 0) { val l = lines.getOrNull(i) ?: break; scopeLines.add(l.trim()); if (l.contains("fun ") || l.contains("class ") || l.contains("def ") || l.contains("function ")) break }
            val selected = editor.getSelectedText().orEmpty()
            JsonObject().apply {
                addProperty("filePath", tab.file.getAbsolutePath()); addProperty("line", line + 1); addProperty("column", column + 1)
                addProperty("currentLine", currentLine); addProperty("selectedText", selected.take(1024))
                addProperty("context", contextLines.joinToString("\n"))
                addProperty("enclosingScope", scopeLines.reversed().joinToString("\n").take(1024))
            }
        }
    }

    private fun resolvePath(path: String): File? {
        val normalized = path.trim()
        if (normalized.isNotBlank() && !normalized.startsWith("file:") && !File(normalized).isAbsolute) {
            resolveRelativePathFromOpenEditor(normalized)?.let { return it }
        }
        return com.rk.ai.resolveWorkspacePath(IdeBridge.workspacePathForResolution(), path)
    }

    private fun resolveRelativePathFromOpenEditor(path: String): File? {
        val activeTab = viewModel.currentTab as? EditorTab
        val activeBase = activeTab?.let { File(it.file.getAbsolutePath()).parentFile }
        activeBase?.let { parent ->
            com.rk.ai.resolveWorkspacePath(IdeBridge.workspacePathForResolution(), File(parent, path).path)?.let { return it }
        }
        val exactMatches = viewModel.tabs.filterIsInstance<EditorTab>().mapNotNull { tab ->
            val tabFile = File(tab.file.getAbsolutePath())
            if (tabFile.path.endsWith(File.separator + path))
                com.rk.ai.resolveWorkspacePath(IdeBridge.workspacePathForResolution(), tabFile.path) else null
        }.distinctBy { it.absolutePath }
        if (exactMatches.size == 1) return exactMatches.first()
        return null
    }
}
