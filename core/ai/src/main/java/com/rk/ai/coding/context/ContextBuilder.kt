package com.rk.ai.coding.context

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.rk.ai.service.EditorOps
import com.rk.ai.service.FileOps
import com.rk.ai.service.GitOps
import com.rk.ai.service.IdeService
import com.rk.ai.service.LspOps
import com.rk.ai.service.ProjectOps
import com.rk.ai.service.TerminalOps

class ContextBuilder(
    private val editorOps: EditorOps,
    private val fileOps: FileOps,
    private val gitOps: GitOps,
    private val terminalOps: TerminalOps,
    private val lspOps: LspOps,
    private val projectOps: ProjectOps,
) {
    constructor(ideService: IdeService) : this(
        editorOps = ideService,
        fileOps = ideService,
        gitOps = ideService,
        terminalOps = ideService,
        lspOps = ideService,
        projectOps = ideService,
    )

    suspend fun build(
        userRequest: String,
        options: ContextBuildOptions = ContextBuildOptions(),
    ): WorkspaceContext {
        val workspaceRoot = runCatching { projectOps.getPrimaryWorkspacePath() }.getOrDefault("")
        val activeJson = runCatching { editorOps.getActiveFile() }.getOrNull()
        val activeFile = activeJson?.toContextFile(options.maxActiveFileChars)?.withFileContentFallback(options.maxActiveFileChars)
        val selection = runCatching { editorOps.getSelection() }
            .getOrDefault("")
            .take(options.maxSelectionChars)
            .ifBlank { activeFile?.selectedText.orEmpty().take(options.maxSelectionChars) }
        val openTabs = runCatching { editorOps.getOpenFiles() }
            .getOrDefault(emptyList())
            .asSequence()
            .map { it.toContextFile(maxContentChars = 0) }
            .filter { it.path.isNotBlank() }
            .take(options.maxOpenTabs)
            .toList()
        val projectMetadata = runCatching { projectOps.getProjectConfig(workspaceRoot).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && !it.contains("\"error\"") }
        val diagnostics = if (activeFile != null && shouldIncludeDiagnostics(userRequest)) {
            runCatching { lspOps.getDiagnostics(activeFile.path) }
                .getOrNull()
                ?.mapNotNull { it.toDiagnostic(activeFile.path) }
                ?.take(options.maxDiagnostics)
                .orEmpty()
        } else {
            emptyList()
        }
        val gitStatus = if (workspaceRoot.isNotBlank() && shouldIncludeGit(userRequest, options)) {
            runCatching { gitOps.getGitStatus(workspaceRoot) }
                .getOrNull()
                ?.takeIf { options.includeCleanGitStatus || (it.get("totalChanges")?.asInt ?: 0) > 0 || shouldIncludeGit(userRequest, options) }
                ?.toString()
        } else {
            null
        }
        val terminalOutput = if (shouldIncludeTerminal(userRequest)) {
            runCatching { terminalOps.getTerminalOutput(options.terminalLines) }
                .getOrNull()
                ?.takeIf { it.isUsefulTerminalOutput() }
        } else {
            null
        }

        return WorkspaceContext(
            workspaceRoot = workspaceRoot,
            currentFile = activeFile,
            currentSelection = selection,
            openTabs = openTabs,
            gitStatus = gitStatus,
            diagnostics = diagnostics,
            terminalOutput = terminalOutput,
            projectMetadata = projectMetadata,
        )
    }

    private suspend fun ContextFile.withFileContentFallback(maxContentChars: Int): ContextFile {
        if (content.isNotBlank() || path.isBlank()) return this
        val file = fileOps.resolvePath(path) ?: return this
        val fallback = fileOps.getFileContent(file.absolutePath)?.take(maxContentChars).orEmpty()
        return copy(content = fallback)
    }

    private fun JsonObject.toContextFile(maxContentChars: Int): ContextFile {
        val cursor = getAsJsonObject("cursor")
        return ContextFile(
            path = string("path"),
            isActive = boolean("isActive"),
            cursorLine = cursor?.int("line"),
            cursorColumn = cursor?.int("character"),
            selectedText = string("selectedText"),
            content = string("content").take(maxContentChars),
        )
    }

    private fun JsonElement.toDiagnostic(filePath: String): ContextDiagnostic? {
        val obj = runCatching { asJsonObject }.getOrNull() ?: return null
        val range = obj.getAsJsonObject("range")
        val start = range?.getAsJsonObject("start")
        return ContextDiagnostic(
            filePath = filePath,
            message = obj.string("message").take(500),
            severity = obj.string("severity"),
            line = start?.int("line") ?: 1,
            column = start?.int("character") ?: 1,
        )
    }

    private fun shouldIncludeGit(request: String, options: ContextBuildOptions): Boolean {
        if (options.includeCleanGitStatus) return true
        return request.containsAny("git", "diff", "status", "commit", "branch", "staged", "unstaged", "changed")
    }

    private fun shouldIncludeDiagnostics(request: String): Boolean =
        request.containsAny("error", "diagnostic", "warning", "lint", "compile", "build", "fix")

    private fun shouldIncludeTerminal(request: String): Boolean =
        request.containsAny("terminal", "output", "log", "test", "build", "run", "crash", "error", "failed", "failure")

    private fun String.containsAny(vararg terms: String): Boolean {
        val normalized = lowercase()
        return terms.any { normalized.contains(it) }
    }

    private fun String.isUsefulTerminalOutput(): Boolean {
        val normalized = trim()
        return normalized.isNotBlank() &&
            normalized != "No active terminal session found" &&
            normalized != "Terminal session is not running" &&
            normalized != "Terminal emulator not available"
    }

    private fun JsonObject.string(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString.orEmpty()

    private fun JsonObject.boolean(name: String): Boolean =
        get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asBoolean ?: false

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asInt
}
