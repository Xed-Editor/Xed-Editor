package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

// ── Terminal Management ──

class ListSessionsTool : BaseMcpTool() {
    override fun getName(): String = "listSessions"
    override fun getDescription(): String = "Lists all active terminal sessions including AI agent sessions."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val sessions = ideService.listSessions()
        return textResult(sessions.toString())
    }
}

class CreateTerminalTool : BaseMcpTool() {
    override fun getName(): String = "createTerminal"
    override fun getDescription(): String = "Creates a new terminal session in the given working directory."
    override fun getRequiredParams(): Map<String, String> = mapOf("workingDir" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("name" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "workingDir" to "Working directory for the terminal"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "name" to "Display name for the terminal (default: 'terminal')"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val workingDir = optionalString(args, "workingDir").ifBlank { ideService.getPrimaryWorkspacePath() }
        val name = optionalString(args, "name").ifBlank { "terminal" }
        val result = ideService.createSession(name, workingDir)
        return textResult(result)
    }
}

class KillTerminalTool : BaseMcpTool() {
    override fun getName(): String = "killTerminal"
    override fun getDescription(): String = "Kills a terminal session by ID."
    override fun getRequiredParams(): Map<String, String> = mapOf("sessionId" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "sessionId" to "Session ID (use 'listSessions' to find available sessions)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val sessionId = requireString(args, "sessionId")
        val result = ideService.killSession(sessionId)
        return textResult(result)
    }
}

class WriteToTerminalTool : BaseMcpTool() {
    override fun getName(): String = "writeToTerminal"
    override fun getDescription(): String = "Writes text to a terminal session."
    override fun getRequiredParams(): Map<String, String> = mapOf("sessionId" to "string", "text" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "sessionId" to "Session ID",
        "text" to "Text to write to the terminal"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val sessionId = requireString(args, "sessionId")
        val text = requireString(args, "text")
        val result = ideService.writeToSession(sessionId, text)
        return textResult(result)
    }
}

class GetTerminalSessionOutputTool : BaseMcpTool() {
    override fun getName(): String = "getTerminalSessionOutput"
    override fun getDescription(): String = "Gets the output from a specific terminal session."
    override fun getRequiredParams(): Map<String, String> = mapOf("sessionId" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("lines" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "sessionId" to "Session ID"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "lines" to "Number of recent lines to retrieve"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val sessionId = requireString(args, "sessionId")
        val lines = optionalPositiveInt(args, "lines")
        val output = ideService.getSessionOutput(sessionId, lines)
        return textResult(output)
    }
}

// ── Clipboard ──

class GetClipboardTool : BaseMcpTool() {
    override fun getName(): String = "getClipboard"
    override fun getDescription(): String = "Gets the current clipboard content."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val content = ideService.getClipboard()
        return textResult(content.ifEmpty { "(clipboard is empty)" })
    }
}

class SetClipboardTool : BaseMcpTool() {
    override fun getName(): String = "setClipboard"
    override fun getDescription(): String = "Sets the clipboard content."
    override fun getRequiredParams(): Map<String, String> = mapOf("text" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "text" to "Text to copy to clipboard"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val text = requireString(args, "text")
        ideService.setClipboard(text)
        return textResult("Copied ${text.length} chars to clipboard")
    }
}

// ── Settings ──

class GetSettingTool : BaseMcpTool() {
    override fun getName(): String = "getSetting"
    override fun getDescription(): String = "Gets an IDE setting value by key."
    override fun getRequiredParams(): Map<String, String> = mapOf("key" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "key" to "Setting key (e.g. 'ai_agent', 'ai_model', 'sandbox')"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val key = requireString(args, "key")
        val value = ideService.getSetting(key)
        return textResult(value ?: "setting '$key' not found or not exposed")
    }
}

class SetSettingTool : BaseMcpTool() {
    override fun getName(): String = "setSetting"
    override fun getDescription(): String = "Sets an IDE setting value by key."
    override fun getRequiredParams(): Map<String, String> = mapOf("key" to "string", "value" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "key" to "Setting key",
        "value" to "New value (will be auto-converted to the correct type)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val key = requireString(args, "key")
        val value = requireString(args, "value")
        ideService.setSetting(key, value)
        return textResult("set $key = $value")
    }
}

class GetAllSettingsTool : BaseMcpTool() {
    override fun getName(): String = "getAllSettings"
    override fun getDescription(): String = "Returns all IDE settings as a JSON object."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val settings = ideService.getAllSettings()
        return textResult(settings.toString())
    }
}

// ── Tab Management ──

class CloseTabTool : BaseMcpTool() {
    override fun getName(): String = "closeTab"
    override fun getDescription(): String = "Closes an editor tab by file path."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path of the file whose tab to close"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(ideService, filePath)
        val result = closeTab(ideService, file.absolutePath)
        return textResult(result)
    }
}

class CloseOtherTabsTool : BaseMcpTool() {
    override fun getName(): String = "closeOtherTabs"
    override fun getDescription(): String = "Closes all editor tabs except the current active one."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val openFiles = ideService.getOpenFiles()
        val activeFile = ideService.getActiveFile()
        val activePath = activeFile?.get("path")?.asString
        val closed = mutableListOf<String>()
        openFiles.forEach { file ->
            val path = file.get("path").asString
            if (path != activePath) {
                closeTab(ideService, path)
                closed.add(path)
            }
        }
        return textResult("closed ${closed.size} tab(s)${if (closed.isNotEmpty()) ": ${closed.joinToString(", ")}" else ""}")
    }
}

// ── Navigation ──

class NavigateToTool : BaseMcpTool() {
    override fun getName(): String = "navigateTo"
    override fun getDescription(): String = "Opens a file at a specific line and column."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("line" to "number", "column" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "line" to "Line number to navigate to (1-indexed, default: 1)",
        "column" to "Column number to navigate to (1-indexed, default: 1)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val line = optionalPositiveInt(args, "line") ?: 1
        val column = optionalPositiveInt(args, "column") ?: 1
        val file = resolvePathOrThrow(ideService, filePath)
        ideService.openFile(file)
        return textResult("navigated to $filePath:$line:$column")
    }
}

// ── Problems/Diagnostics ──

class GetProblemsTool : BaseMcpTool() {
    override fun getName(): String = "getProblems"
    override fun getDescription(): String = "Returns all LSP diagnostics across all open files."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val openFiles = ideService.getOpenFiles()
        val result = com.google.gson.JsonObject()
        val problems = com.google.gson.JsonArray()
        openFiles.forEach { file ->
            val path = file.get("path").asString
            runCatching {
                val diags = ideService.getDiagnostics(path)
                if (diags.size() > 0) {
                    problems.add(com.google.gson.JsonObject().apply {
                        addProperty("filePath", path)
                        add("diagnostics", diags)
                    })
                }
            }
        }
        result.add("problems", problems)
        result.addProperty("totalFilesWithIssues", problems.size())
        return textResult(result.toString())
    }
}

// ── File Tree ──

class GetFileTreeTool : BaseMcpTool() {
    override fun getName(): String = "getFileTree"
    override fun getDescription(): String = "Returns the file tree at the specified directory path."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "maxDepth" to "number", "maxItems" to "number"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Directory path to explore (default: workspace root)",
        "maxDepth" to "Maximum directory depth (default: 2, max: 5)",
        "maxItems" to "Maximum items to return (default: 100, max: 500)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = getPathParam(args)
        val maxDepth = (optionalPositiveInt(args, "maxDepth") ?: 2).coerceIn(1, 5)
        val maxItems = (optionalPositiveInt(args, "maxItems") ?: 100).coerceIn(1, 500)
        val dir = if (path != null) {
            resolvePathOrThrow(ideService, path)
        } else {
            java.io.File(ideService.getPrimaryWorkspacePath())
        }
        if (!dir.exists() || !dir.isDirectory) throw ToolError.PathOutsideWorkspace("not a directory: $path")
        val tree = buildFileTree(dir, maxDepth, maxItems)
        return textResult(tree)
    }
}

// ── Git Blame ──

class GetGitBlameTool : BaseMcpTool() {
    override fun getName(): String = "getGitBlame"
    override fun getDescription(): String = "Returns git blame information for a file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(ideService, filePath)
        val workspacePath = ideService.getPrimaryWorkspacePath()
        val result = runGitBlame(file, workspacePath)
        return textResult(result)
    }
}

// ── Bookmark Management ──

class ToggleBookmarkTool : BaseMcpTool() {
    override fun getName(): String = "toggleBookmark"
    override fun getDescription(): String = "Toggles a bookmark on a line in a file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "line" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file",
        "line" to "Line number (1-indexed)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val line = requireInt(args, "line")
        val result = ideService.toggleBookmark(filePath, line)
        return textResult(result)
    }
}

class ListBookmarksTool : BaseMcpTool() {
    override fun getName(): String = "listBookmarks"
    override fun getDescription(): String = "Lists all bookmarks across open files."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val bookmarks = ideService.listBookmarks()
        return textResult(bookmarks.toString())
    }
}

// ── Helpers ──

private suspend fun closeTab(ideService: IdeService, filePath: String): String {
    return ideService.closeTab(filePath)
}

private suspend fun buildFileTree(dir: java.io.File, maxDepth: Int, maxItems: Int): String {
    val ignored = setOf(".git", ".gradle", ".idea", "build", "node_modules", ".dex", ".cache")
    val sb = StringBuilder()
    sb.appendLine("[D] ${dir.name}/")
    var count = 0
    dir.walkTopDown()
        .maxDepth(maxDepth)
        .onEnter { it.name !in ignored && !it.isHidden }
        .filter { it != dir }
        .take(maxItems)
        .forEach { child ->
            val depth = child.toRelativeString(dir).split(java.io.File.separator).size
            val indent = "  ".repeat(depth.coerceAtMost(maxDepth))
            sb.appendLine("$indent${if (child.isDirectory) "[D]" else "[F]"} ${child.name}")
            count++
        }
    if (count >= maxItems) sb.appendLine("  ... (truncated at $maxItems items)")
    return sb.toString()
}

private suspend fun runGitBlame(file: java.io.File, workspacePath: String): String {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val root = com.rk.utils.findGitRoot(workspacePath) ?: return@withContext "not a git repository"
            val relative = file.toRelativeString(java.io.File(root))
            val process = ProcessBuilder(
                "git", "blame", "--line-porcelain", relative
            ).directory(java.io.File(root)).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            if (output.isBlank()) "no blame info (file may be uncommitted)"
            else {
                val lines = output.split("\n").filter { it.startsWith("author ") || it.startsWith("author-mail ") || it.startsWith("author-time ") }
                val summary = lines.chunked(3).mapIndexed { i, chunk ->
                    val author = chunk.find { it.startsWith("author ") }?.removePrefix("author ") ?: "?"
                    val time = chunk.find { it.startsWith("author-time ") }?.removePrefix("author-time ")?.toLongOrNull()
                    val date = if (time != null) {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(time * 1000))
                    } else "?"
                    "line ${i + 1}: $author ($date)"
                }.joinToString("\n")
                summary
            }
        }.getOrElse { "git blame failed: ${it.message}" }
    }
}
