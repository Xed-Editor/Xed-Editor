package com.rk.ai.service

import com.google.gson.JsonObject
import com.rk.ai.AiConfig
import com.rk.ai.IdeBridge
import com.rk.ai.displayRootFor
import com.rk.ai.resolveRelativePathFromOpenEditor
import com.rk.ai.resolveWorkspacePath
import com.rk.tabs.editor.EditorTab
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FileService(tabRepository: TabRepository) {

    private val pathCache = LinkedHashMap<String, File>(32, 0.75f, true)
    private val pathCacheMaxSize = 128

    fun resolvePath(path: String): File? {
        val normalized = path.trim()
        pathCache[normalized]?.let { if (it.exists()) return it }
        val resolved = if (normalized.isNotBlank() && !normalized.startsWith("file:") && !File(normalized).isAbsolute) {
            resolveRelativePathFromOpenEditor(normalized, tabRepository) ?: resolveWorkspacePath(IdeBridge.workspacePathForResolution(), path)
        } else {
            resolveWorkspacePath(IdeBridge.workspacePathForResolution(), path)
        }
        if (resolved != null) {
            pathCache[normalized] = resolved
            if (pathCache.size > pathCacheMaxSize) {
                val oldest = pathCache.keys.firstOrNull()
                if (oldest != null) pathCache.remove(oldest)
            }
        }
        return resolved
    }

    fun listFiles(directory: File, recursive: Boolean, maxFiles: Int): List<String> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()
        val ignored = AiConfig.ignoredDirectories
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
            val tab = tabRepository.tabs.filterIsInstance<EditorTab>().find { tab ->
            } ?: return
            if (!force && tab.editorState.isDirty) return
            tab.refresh()
        } else {
            tabRepository.tabs.filterIsInstance<EditorTab>().forEach {
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
            findTabByPath(file.absolutePath)?.let { tabRepository.tabManager.removeTab(it) }
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
            findTabByPath(source.absolutePath)?.let { tabRepository.tabManager.removeTab(it) }
        }
        withContext(Dispatchers.IO) {
            dest.parentFile?.mkdirs()
            source.renameTo(dest)
        }
        refreshEditors(filePath = dest.absolutePath, force = true)
        return "renamed ${source.absolutePath} -> ${dest.absolutePath}"
    }

    fun clearCache() { pathCache.clear() }

    private fun findTabByPath(path: String): EditorTab? {
        val file = File(path).absoluteFile
        return tabRepository.tabs.filterIsInstance<EditorTab>().find {
            File(it.file.getAbsolutePath()).absoluteFile == file
        }
    }
}
