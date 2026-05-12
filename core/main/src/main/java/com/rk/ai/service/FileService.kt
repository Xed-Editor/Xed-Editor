package com.rk.ai.service

import com.google.gson.JsonObject
import com.rk.activities.main.MainViewModel
import com.rk.ai.IdeBridge
import com.rk.ai.displayRootFor
import com.rk.ai.resolveWorkspacePath
import com.rk.file.FileWrapper
import com.rk.tabs.editor.EditorTab
import java.io.File
import java.util.WeakHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FileService(private val viewModel: MainViewModel) {

    private val pathCache = WeakHashMap<String, File>()

    fun resolvePath(path: String): File? {
        val normalized = path.trim()
        pathCache[normalized]?.let { if (it.exists()) return it }
        val resolved = if (normalized.isNotBlank() && !normalized.startsWith("file:") && !File(normalized).isAbsolute) {
            resolveRelativePathFromOpenEditor(normalized) ?: resolveWorkspacePath(IdeBridge.workspacePathForResolution(), path)
        } else {
            resolveWorkspacePath(IdeBridge.workspacePathForResolution(), path)
        }
        if (resolved != null) pathCache[normalized] = resolved
        return resolved
    }

    private fun resolveRelativePathFromOpenEditor(path: String): File? {
        val activeTab = viewModel.currentTab as? EditorTab
        val activeBase = activeTab?.let { File(it.file.getAbsolutePath()).parentFile }
        activeBase?.let { parent ->
            resolveWorkspacePath(IdeBridge.workspacePathForResolution(), File(parent, path).path)?.let { return it }
        }
        val exactMatches = viewModel.tabs
            .filterIsInstance<EditorTab>()
            .mapNotNull { tab ->
                val tabFile = File(tab.file.getAbsolutePath())
                if (tabFile.path.endsWith(File.separator + path)) {
                    resolveWorkspacePath(IdeBridge.workspacePathForResolution(), tabFile.path)
                } else null
            }
            .distinctBy { it.absolutePath }
        if (exactMatches.size == 1) return exactMatches.first()
        return null
    }

    fun listFiles(directory: File, recursive: Boolean, maxFiles: Int): List<String> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()
        val ignored = setOf(".git", ".gradle", ".idea", "build", "node_modules", ".dex", ".cache", ".dex")
        val root = displayRootFor(IdeBridge.workspacePathForResolution(), directory)
        val output = mutableListOf<String>()
        if (recursive) {
            directory.walkTopDown()
                .onEnter { it.name !in ignored }
                .forEach { child ->
                    if (output.size >= maxFiles) return@forEach
                    if (child == directory) return@forEach
                    val relative = child.relativeToOrSelf(root).path + if (child.isDirectory) "/" else ""
                    output.add(relative)
                }
        } else {
            directory.listFiles()
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?.forEach { child ->
                    if (output.size >= maxFiles) return@forEach
                    if (child.name in ignored) return@forEach
                    val relative = child.relativeToOrSelf(root).path + if (child.isDirectory) "/" else ""
                    output.add(relative)
                }
        }
        return output
    }

    suspend fun getFileContent(filePath: String): String? {
        val openTab = findTabByPath(filePath)
        return if (openTab != null) {
            withContext(Dispatchers.Main) {
                openTab.editorState.editor.get()?.text?.toString()
            }
        } else {
            withContext(Dispatchers.IO) {
                val file = File(filePath)
                if (!file.exists() || !file.isFile) return@withContext null
                val length = file.length()
                if (length > 10 * 1024 * 1024) {
                    file.useLines { it.take(5000).joinToString("\n") }
                } else {
                    file.readText()
                }
            }
        }
    }

    suspend fun writeFile(file: File, content: String) {
        val tab = findTabByPath(file.absolutePath)
        withContext(Dispatchers.IO) {
            tab?.saveMutex?.withLock {
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            } ?: run {
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            }
        }
        if (tab != null) {
            withContext(Dispatchers.Main) { tab.refresh() }
        }
    }

    fun refreshEditors(filePath: String?, force: Boolean) {
        if (filePath != null) {
            val canonical = File(filePath).absoluteFile
            val tab = viewModel.tabs.filterIsInstance<EditorTab>().find { tab ->
                File(tab.file.getAbsolutePath()).absoluteFile == canonical
            } ?: return
            if (!force && tab.editorState.isDirty) return
            tab.refresh()
        } else {
            viewModel.tabs.filterIsInstance<EditorTab>().forEach {
                if (force || !it.editorState.isDirty) it.refresh()
            }
        }
    }

    suspend fun createFile(filePath: String, content: String?): String {
        val file = resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace: $filePath")
        if (file.exists()) throw IllegalArgumentException("file already exists: $filePath")
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            if (content != null) file.writeText(content, Charsets.UTF_8)
            else file.createNewFile()
        }
        refreshEditors(filePath = file.absolutePath, force = true)
        return "created ${file.absolutePath}"
    }

    suspend fun deleteFile(filePath: String): String {
        val file = resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace: $filePath")
        if (!file.exists()) throw IllegalArgumentException("file not found: $filePath")
        if (!file.isFile) throw IllegalArgumentException("not a file: $filePath")
        withContext(Dispatchers.Main) {
            findTabByPath(file.absolutePath)?.let { viewModel.tabManager.removeTab(it) }
        }
        withContext(Dispatchers.IO) { file.delete() }
        return "deleted ${file.absolutePath}"
    }

    suspend fun renameFile(sourcePath: String, destPath: String): String {
        val source = resolvePath(sourcePath) ?: throw IllegalArgumentException("path outside workspace: $sourcePath")
        val dest = resolvePath(destPath) ?: throw IllegalArgumentException("path outside workspace: $destPath")
        if (!source.exists()) throw IllegalArgumentException("source not found: $sourcePath")
        if (dest.exists()) throw IllegalArgumentException("destination already exists: $destPath")
        withContext(Dispatchers.Main) {
            findTabByPath(source.absolutePath)?.let { viewModel.tabManager.removeTab(it) }
        }
        withContext(Dispatchers.IO) {
            dest.parentFile?.mkdirs()
            source.renameTo(dest)
        }
        refreshEditors(filePath = dest.absolutePath, force = true)
        return "renamed ${source.absolutePath} -> ${dest.absolutePath}"
    }

    private fun findTabByPath(path: String): EditorTab? {
        val file = File(path).absoluteFile
        return viewModel.tabs.filterIsInstance<EditorTab>().find {
            File(it.file.getAbsolutePath()).absoluteFile == file
        }
    }
}
