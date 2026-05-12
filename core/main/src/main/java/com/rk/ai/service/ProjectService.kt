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

    suspend fun searchCode(query: String, limit: Int): JsonArray {
        val app = application ?: return JsonArray()
        val vm = searchViewModel()
        val results = withContext(Dispatchers.IO) {
            vm.searchCode(
                context = app, mainViewModel = viewModel,
                projectRoot = File(IdeBridge.primaryWorkspacePath()).toFileWrapper(),
                query = query, useIndex = Settings.always_index_projects
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

    suspend fun findFiles(query: String, limit: Int): JsonArray {
        val app = application ?: return JsonArray()
        val vm = searchViewModel()
        val results = withContext(Dispatchers.IO) {
            vm.searchFileName(
                context = app,
                projectRoot = File(IdeBridge.primaryWorkspacePath()).toFileWrapper(),
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

    suspend fun getProjectStructure(path: String, maxDepth: Int, maxItems: Int): String {
        val dir = resolvePath(path) ?: throw IllegalArgumentException("path outside workspace: $path")
        if (!dir.exists() || !dir.isDirectory) throw IllegalArgumentException("not a directory: $path")
        val ignored = AiConfig.ignoredDirectories
        val output = StringBuilder(4096)
        var count = 0
        withContext(Dispatchers.IO) {
            output.appendLine("[D] ${dir.name}/")
            dir.walkTopDown()
                .maxDepth(maxDepth)
                .onEnter { it.name !in ignored && !it.isHidden }
                .forEach { child ->
                    if (count >= maxItems || child == dir) return@forEach
                    val depth = child.toRelativeString(dir).split(File.separator).size
                    val indent = "  ".repeat(depth.coerceAtMost(maxDepth))
                    output.appendLine("$indent${if (child.isDirectory) "[D]" else "[F]"} ${child.name}")
                    count++
                }
            if (count >= maxItems) output.appendLine("  ... (truncated at $maxItems items)")
        }
        return output.toString()
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
        if (normalized.isNotBlank() && !normalized.startsWith("file:") && !File(normalized).isAbsolute) {
            resolveRelativePathFromOpenEditor(normalized, tabRepo)?.let { return it }
        }
        return com.rk.ai.resolveWorkspacePath(IdeBridge.workspacePathForResolution(), path)
    }
}
