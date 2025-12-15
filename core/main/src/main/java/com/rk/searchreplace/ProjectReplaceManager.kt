package com.rk.searchreplace

import androidx.compose.runtime.mutableStateOf
import com.rk.file.FileObject
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

object ProjectReplaceManager {
    private val undoStack = ArrayDeque<ReplaceOperation>()
    private val redoStack = ArrayDeque<ReplaceOperation>()

    val canUndo = mutableStateOf(false)
    val canRedo = mutableStateOf(false)
    val lastSummary = mutableStateOf<String?>(null)

    private fun syncState() {
        canUndo.value = undoStack.isNotEmpty()
        canRedo.value = redoStack.isNotEmpty()
    }

    data class FileChange(
        val file: FileObject,
        val before: String,
        val after: String,
    )

    data class ReplaceOperation(
        val query: String,
        val replacement: String,
        val changes: List<FileChange>,
    )

    /**
     * Search options for controlling how matching is performed
     */
    data class SearchOptions(
        val caseSensitive: Boolean = false,
        val wholeWord: Boolean = false,
        val useRegex: Boolean = false,
    )

    /**
     * Build a Regex pattern based on the search options
     */
    fun buildSearchRegex(query: String, options: SearchOptions): Regex {
        val pattern = if (options.useRegex) {
            if (options.wholeWord) "\\b$query\\b" else query
        } else {
            val escaped = Regex.escape(query)
            if (options.wholeWord) "\\b$escaped\\b" else escaped
        }
        
        val regexOptions = if (options.caseSensitive) {
            emptySet()
        } else {
            setOf(RegexOption.IGNORE_CASE)
        }
        
        return pattern.toRegex(regexOptions)
    }

    suspend fun replaceAllInProject(
        projectRoot: FileObject,
        query: String,
        replacement: String,
        options: SearchOptions = SearchOptions(),
        charset: Charset = Charsets.UTF_8,
        maxFileBytes: Long = 5L * 1024L * 1024L,
    ): ReplaceOperation? =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext null

            val regex = runCatching { buildSearchRegex(query, options) }.getOrElse {
                toast("Invalid regex pattern")
                return@withContext null
            }
            
            val files = collectTextFiles(projectRoot, maxFileBytes)
            val changes = ArrayList<FileChange>()

            for (file in files) {
                val before = runCatching { file.readText(charset) }.getOrNull() ?: continue
                if (!before.contains(regex)) continue

                val after = before.replace(regex, replacement)
                if (after == before) continue

                val wrote = runCatching {
                    file.writeText(after, charset)
                    true
                }.getOrDefault(false)
                if (!wrote) continue

                changes.add(FileChange(file = file, before = before, after = after))
            }

            if (changes.isEmpty()) {
                lastSummary.value = "No matches found"
                toast(lastSummary.value)
                return@withContext null
            }

            val op = ReplaceOperation(query = query, replacement = replacement, changes = changes)
            undoStack.addLast(op)
            redoStack.clear()
            syncState()

            lastSummary.value = "Replaced in ${changes.size} file(s)"
            toast(lastSummary.value)

            op
        }

    suspend fun undoLastReplace(): Boolean =
        withContext(Dispatchers.IO) {
            val op = undoStack.removeLastOrNull() ?: return@withContext false

            var failures = 0
            for (change in op.changes) {
                val ok = runCatching {
                    change.file.writeText(change.before)
                    true
                }.getOrDefault(false)
                if (!ok) failures++
            }

            redoStack.addLast(op)
            syncState()

            lastSummary.value =
                if (failures == 0) {
                    "Undo: restored ${op.changes.size} file(s)"
                } else {
                    "Undo: restored ${op.changes.size - failures}/${op.changes.size} file(s)"
                }
            toast(lastSummary.value)

            failures == 0
        }

    suspend fun redoLastReplace(): Boolean =
        withContext(Dispatchers.IO) {
            val op = redoStack.removeLastOrNull() ?: return@withContext false

            var failures = 0
            for (change in op.changes) {
                val ok = runCatching {
                    change.file.writeText(change.after)
                    true
                }.getOrDefault(false)
                if (!ok) failures++
            }

            undoStack.addLast(op)
            syncState()

            lastSummary.value =
                if (failures == 0) {
                    "Redo: applied to ${op.changes.size} file(s)"
                } else {
                    "Redo: applied to ${op.changes.size - failures}/${op.changes.size} file(s)"
                }
            toast(lastSummary.value)

            failures == 0
        }

    private suspend fun collectTextFiles(root: FileObject, maxFileBytes: Long): List<FileObject> {
        val out = ArrayList<FileObject>()
        collectTextFilesInto(root, maxFileBytes, out)
        return out
    }

    private suspend fun collectTextFilesInto(node: FileObject, maxFileBytes: Long, out: MutableList<FileObject>) {
        if (node.isSymlink()) return

        if (node.isDirectory()) {
            val name = node.getName()
            if (name == ".git" || name == ".gradle" || name == "build" || name == "node_modules") return

            val children = runCatching { node.listFiles() }.getOrDefault(emptyList())
            for (child in children) {
                collectTextFilesInto(child, maxFileBytes, out)
            }
            return
        }

        if (!node.isFile()) return
        if (!node.canRead() || !node.canWrite()) return

        val len = runCatching { node.length() }.getOrDefault(0L)
        if (len <= 0L || len > maxFileBytes) return

        // Quick binary sniff: skip files containing NUL bytes
        val isBinary = runCatching {
                node.getInputStream().use { input ->
                    val buf = ByteArray(4096)
                    val read = input.read(buf)
                    if (read <= 0) return@use false
                    for (i in 0 until read) {
                        if (buf[i].toInt() == 0) return@use true
                    }
                    false
                }
            }
            .getOrDefault(true)

        if (isBinary) return

        out.add(node)
    }
}
