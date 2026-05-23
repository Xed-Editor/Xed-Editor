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

class FileService(private val tabRepository: TabRepository) {

    private val pathCache = LinkedHashMap<String, PathCacheEntry>(32, 0.75f, true)
    private val contentCache = LinkedHashMap<String, ContentCacheEntry>(16, 0.75f, true)
    private val cacheLock = Any()

    private class PathCacheEntry(val file: File, val timestamp: Long)
    private class ContentCacheEntry(val content: String, val fileModified: Long, val timestamp: Long)

    fun resolvePath(path: String): File? {
        val normalized = path.trim()
        synchronized(cacheLock) {
            pathCache[normalized]?.let {
                if (it.file.exists() && System.currentTimeMillis() - it.timestamp < 5000) return it.file
            }
        }
        val resolved = if (normalized.isNotBlank() && !normalized.startsWith("file:") && !File(normalized).isAbsolute) {
            resolveRelativePathFromOpenEditor(normalized, tabRepository) ?: resolveWorkspacePath(IdeBridge.workspacePathForResolution(), path)
        } else {
            resolveWorkspacePath(IdeBridge.workspacePathForResolution(), path)
        }
        // Canonicalize to prevent path traversal
        val safe = resolved?.let { file ->
            val canonical = file.canonicalPath
            val workspaceRoot = IdeBridge.workspacePathForResolution()
            if (workspaceRoot.isNotBlank()) {
                val rootCanonical = File(workspaceRoot).canonicalPath
                if (canonical.startsWith(rootCanonical)) file else null
            } else file
        }
        if (safe != null) {
            synchronized(cacheLock) {
                pathCache[normalized] = PathCacheEntry(safe, System.currentTimeMillis())
                trimToMaxSize(pathCache, 128)
            }
        }
        return safe
    }

    fun listFiles(directory: File, recursive: Boolean, maxFiles: Int): List<String> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()
        val ignored = AiConfig.ignoredDirectories
        val root = displayRootFor(IdeBridge.workspacePathForResolution(), directory)
        val output = mutableListOf<String>()
        if (recursive) {
            directory.walkTopDown()
                .onEnter { it.name !in ignored }
                .filter { it != directory }
                .take(maxFiles)
                .forEach { child ->
                    val relative = child.relativeToOrSelf(root).path + if (child.isDirectory) "/" else ""
                    output.add(relative)
                }
        } else {
            directory.listFiles()
                ?.filter { it.name !in ignored }
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?.take(maxFiles)
                ?.forEach { child ->
                    val relative = child.relativeToOrSelf(root).path + if (child.isDirectory) "/" else ""
                    output.add(relative)
                }
        }
        return output
    }

    suspend fun getFileContent(filePath: String, startLine: Int? = null, endLine: Int? = null): String? {
        val openTab = findTabByPath(filePath)
        if (openTab != null && startLine == null && endLine == null) {
            return withContext(Dispatchers.Main) {
                openTab.editorState.editor.get()?.text?.toString()
            }
        }
        val canonical = File(filePath).absolutePath
        val now = System.currentTimeMillis()
        if (startLine == null && endLine == null) {
            synchronized(cacheLock) {
                val cached = contentCache[canonical]
                val diskFile = File(canonical)
                if (cached != null && diskFile.exists()) {
                    val diskModified = diskFile.lastModified()
                    if (cached.fileModified == diskModified && now - cached.timestamp < 5000) return cached.content
                }
            }
        }
        return withContext(Dispatchers.IO) {
            val file = File(canonical)
            if (!file.exists() || !file.isFile) return@withContext null
            val length = file.length()
            val lastModified = file.lastModified()
            
            val content = if (startLine != null || endLine != null) {
                val s = startLine ?: 1
                val e = endLine ?: Int.MAX_VALUE
                file.useLines { lines ->
                    lines.drop(s - 1).take(e - s + 1).joinToString("\n")
                }
            } else if (length > 10 * 1024 * 1024) {
                file.useLines { it.take(5000).joinToString("\n") } + "\n\n... (file >10MB, truncated to 5000 lines)"
            } else {
                file.readText()
            }
            
            if (startLine == null && endLine == null && length <= 1_048_576L) {
                synchronized(cacheLock) {
                    contentCache[canonical] = ContentCacheEntry(content, lastModified, now)
                    trimToMaxSize(contentCache, 64)
                }
            }
            content
        }
    }

    suspend fun writeFile(file: File, content: String) {
        synchronized(cacheLock) { contentCache.remove(file.absolutePath) }
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
            val tab = tabRepository.tabs.filterIsInstance<EditorTab>().find { t ->
                File(t.file.getAbsolutePath()).absoluteFile == canonical
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

    private fun trimToMaxSize(map: LinkedHashMap<*, *>, maxSize: Int) {
        while (map.size > maxSize) {
            val oldest = map.keys.firstOrNull() ?: break
            map.remove(oldest)
        }
    }

    fun clearCache() { synchronized(cacheLock) { pathCache.clear(); contentCache.clear() } }

    private fun findTabByPath(path: String): EditorTab? {
        val file = File(path).absoluteFile
        return tabRepository.tabs.filterIsInstance<EditorTab>().find {
            File(it.file.getAbsolutePath()).absoluteFile == file
        }
    }
}