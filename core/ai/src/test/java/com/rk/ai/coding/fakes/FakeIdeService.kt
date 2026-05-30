package com.rk.ai.coding.fakes

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.service.CommandResult
import com.rk.ai.service.IdeService
import java.io.File

class FakeIdeService(
    private val root: File,
) : IdeService {
    data class Patch(val filePath: String, val oldContent: String, val newContent: String, val title: String)

    val files = linkedMapOf<String, String>()
    val diagnostics = linkedMapOf<String, JsonArray>()
    var references: JsonArray = JsonArray()
    var definitions: JsonArray = JsonArray()
    var activeFilePath: String = ""
    var selection: String = ""
    var terminalOutput: String = ""
    var gitStatus: JsonObject = JsonObject().apply {
        addProperty("branch", "main")
        add("changes", JsonArray())
        addProperty("totalChanges", 0)
    }
    var gitDiff: String = "no changes"
    var lastPatch: Patch? = null

    fun putFile(relativePath: String, content: String): String {
        val path = resolvePath(relativePath)?.absolutePath ?: File(root, relativePath).absolutePath
        files[path] = content
        if (activeFilePath.isBlank()) activeFilePath = path
        return path
    }

    override fun resolvePath(path: String): File? {
        val file = if (File(path).isAbsolute) File(path) else File(root, path)
        val canonicalRoot = root.absoluteFile.normalizePath()
        val canonicalFile = file.absoluteFile.normalizePath()
        return if (canonicalFile.startsWith(canonicalRoot)) File(canonicalFile) else null
    }

    override fun listFiles(directory: File, recursive: Boolean, maxFiles: Int): List<String> {
        val dirPath = directory.absoluteFile.normalizePath().trimEnd('/')
        return files.keys
            .asSequence()
            .filter { it.startsWith(dirPath) }
            .map { File(it).relativeTo(root).path }
            .filter { recursive || !it.contains(File.separatorChar) }
            .take(maxFiles)
            .toList()
    }

    override suspend fun getFileContent(filePath: String, startLine: Int?, endLine: Int?): String? {
        val text = files[resolvePath(filePath)?.absolutePath ?: filePath] ?: return null
        if (startLine == null && endLine == null) return text
        val start = (startLine ?: 1).coerceAtLeast(1)
        val end = endLine ?: Int.MAX_VALUE
        return text.lines().drop(start - 1).take(end - start + 1).joinToString("\n")
    }

    override suspend fun writeFile(file: File, content: String) {
        files[file.absolutePath] = content
    }

    override fun refreshEditors(filePath: String?, force: Boolean) = Unit

    override suspend fun createFile(filePath: String, content: String?): String {
        val file = resolvePath(filePath) ?: error("outside workspace")
        files[file.absolutePath] = content.orEmpty()
        return "created ${file.absolutePath}"
    }

    override suspend fun deleteFile(filePath: String): String {
        val file = resolvePath(filePath) ?: error("outside workspace")
        files.remove(file.absolutePath)
        return "deleted ${file.absolutePath}"
    }

    override suspend fun renameFile(sourcePath: String, destPath: String): String {
        val source = resolvePath(sourcePath) ?: error("outside workspace")
        val dest = resolvePath(destPath) ?: error("outside workspace")
        files[dest.absolutePath] = files.remove(source.absolutePath).orEmpty()
        return "renamed ${source.absolutePath} -> ${dest.absolutePath}"
    }

    override fun showPatch(filePath: String, oldContent: String, newContent: String, title: String, onApply: suspend () -> Unit) {
        lastPatch = Patch(filePath, oldContent, newContent, title)
    }

    override fun applyBatchEdits(edits: Map<String, String>, title: String) = Unit
    override fun rejectPatch(filePath: String) = Unit
    override fun openFile(file: File) { activeFilePath = file.absolutePath }

    override suspend fun getOpenFiles(): List<JsonObject> =
        files.keys.map { path -> fileJson(path, active = path == activeFilePath, includeContent = false) }

    override suspend fun getActiveFile(): JsonObject? =
        activeFilePath.takeIf { it.isNotBlank() }?.let { fileJson(it, active = true, includeContent = true) }

    override suspend fun getSelection(): String = selection
    override fun replaceSelection(newContent: String) { selection = newContent }
    override fun insertAtCursor(newContent: String) = Unit
    override suspend fun saveAllFiles(): String = "saved 0 dirty open file(s)"
    override fun ensureIdeEnabled() = Unit
    override fun showMessage(message: String) = Unit

    override suspend fun getDiagnostics(filePath: String): JsonArray = diagnostics[filePath] ?: JsonArray()
    override suspend fun findDefinitions(filePath: String, line: Int, column: Int): JsonArray = definitions
    override suspend fun findReferences(filePath: String, line: Int, column: Int): JsonArray = references
    override fun renameSymbol(filePath: String, line: Int, column: Int, newName: String) = Unit
    override suspend fun formatDocument(filePath: String) = Unit

    override suspend fun getGitStatus(workspacePath: String): JsonObject = gitStatus
    override suspend fun getGitDiff(workspacePath: String): String = gitDiff
    override suspend fun gitCommit(workspacePath: String, message: String, all: Boolean): String = "committed abc1234: $message"
    override suspend fun gitCheckout(workspacePath: String, target: String): String = "checked out $target"

    override suspend fun runCommand(command: String, timeoutSeconds: Long): CommandResult =
        CommandResult(output = "", error = "command execution disabled in fake", exitCode = 1, timedOut = false)

    override suspend fun getTerminalOutput(lines: Int?): String = terminalOutput.lines().takeLast(lines ?: Int.MAX_VALUE).joinToString("\n")

    override fun getPrimaryWorkspacePath(): String = root.absolutePath

    override suspend fun searchCode(query: String, limit: Int, path: String?, isRegex: Boolean): JsonArray {
        val regex = if (isRegex) runCatching { Regex(query) }.getOrNull() else null
        val results = JsonArray()
        files.forEach { (filePath, content) ->
            content.lines().forEachIndexed { index, line ->
                val matches = regex?.containsMatchIn(line) ?: line.contains(query, ignoreCase = true)
                if (matches && results.size() < limit) {
                    results.add(JsonObject().apply {
                        addProperty("path", filePath)
                        addProperty("line", index + 1)
                        addProperty("column", line.indexOf(query).coerceAtLeast(0) + 1)
                        addProperty("snippet", line)
                    })
                }
            }
        }
        return results
    }

    override suspend fun searchSymbols(query: String, limit: Int, path: String?): JsonArray = searchCode(query, limit, path, false)

    override suspend fun findFiles(query: String, limit: Int, path: String?): JsonArray = JsonArray().apply {
        files.keys.filter { it.contains(query) || File(it).name.contains(query) }.take(limit).forEach { filePath ->
            add(JsonObject().apply {
                addProperty("path", filePath)
                addProperty("name", File(filePath).name)
            })
        }
    }

    override suspend fun getProjectStructure(path: String, maxDepth: Int, maxItems: Int): String = "[D] ${root.name}/"

    override suspend fun getProjectConfig(workspacePath: String): JsonObject = JsonObject().apply {
        addProperty("workspace", root.absolutePath)
        addProperty("language", "Kotlin")
        addProperty("buildSystem", "Gradle")
    }

    override suspend fun getSymbolUnderCursor(): JsonObject = JsonObject()

    private fun fileJson(path: String, active: Boolean, includeContent: Boolean): JsonObject = JsonObject().apply {
        addProperty("path", path)
        addProperty("isActive", active)
        add("cursor", JsonObject().apply {
            addProperty("line", 1)
            addProperty("character", 1)
        })
        addProperty("selectedText", selection)
        if (includeContent) addProperty("content", files[path].orEmpty())
    }

    private fun File.normalizePath(): String = path.replace('\\', '/')
}
