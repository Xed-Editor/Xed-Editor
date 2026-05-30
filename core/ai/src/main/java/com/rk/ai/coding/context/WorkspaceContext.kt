package com.rk.ai.coding.context

data class WorkspaceContext(
    val workspaceRoot: String,
    val currentFile: ContextFile? = null,
    val currentSelection: String = "",
    val openTabs: List<ContextFile> = emptyList(),
    val gitStatus: String? = null,
    val diagnostics: List<ContextDiagnostic> = emptyList(),
    val terminalOutput: String? = null,
    val projectMetadata: String? = null,
) {
    fun isEmpty(): Boolean =
        workspaceRoot.isBlank() &&
            currentFile == null &&
            currentSelection.isBlank() &&
            openTabs.isEmpty() &&
            gitStatus.isNullOrBlank() &&
            diagnostics.isEmpty() &&
            terminalOutput.isNullOrBlank() &&
            projectMetadata.isNullOrBlank()

    fun toPrompt(): String {
        if (isEmpty()) return ""
        return buildString {
            appendLine("Workspace context:")
            if (workspaceRoot.isNotBlank()) appendLine("- Workspace root: $workspaceRoot")
            projectMetadata?.takeIf { it.isNotBlank() }?.let {
                appendLine("- Project metadata: $it")
            }
            currentFile?.let { file ->
                appendLine("- Current file: ${file.path}")
                file.cursorLine?.let { line ->
                    val column = file.cursorColumn ?: 1
                    appendLine("- Cursor: line $line, column $column")
                }
                if (file.content.isNotBlank()) {
                    appendLine()
                    appendLine("Current file excerpt:")
                    appendLine("```")
                    appendLine(file.content)
                    appendLine("```")
                }
            }
            if (currentSelection.isNotBlank()) {
                appendLine()
                appendLine("Current selection:")
                appendLine("```")
                appendLine(currentSelection)
                appendLine("```")
            }
            if (openTabs.isNotEmpty()) {
                appendLine()
                appendLine("Open tabs:")
                openTabs.forEach { tab ->
                    val active = if (tab.isActive) " active" else ""
                    appendLine("- ${tab.path}$active")
                }
            }
            gitStatus?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("Git status:")
                appendLine(it)
            }
            if (diagnostics.isNotEmpty()) {
                appendLine()
                appendLine("Diagnostics:")
                diagnostics.forEach { diagnostic ->
                    appendLine("- ${diagnostic.filePath}:${diagnostic.line}:${diagnostic.column} ${diagnostic.severity}: ${diagnostic.message}")
                }
            }
            terminalOutput?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("Recent terminal output:")
                appendLine("```")
                appendLine(it)
                appendLine("```")
            }
        }.trim()
    }
}

data class ContextFile(
    val path: String,
    val isActive: Boolean = false,
    val cursorLine: Int? = null,
    val cursorColumn: Int? = null,
    val selectedText: String = "",
    val content: String = "",
)

data class ContextDiagnostic(
    val filePath: String,
    val message: String,
    val severity: String,
    val line: Int,
    val column: Int,
)

data class ContextBuildOptions(
    val maxActiveFileChars: Int = 12_000,
    val maxSelectionChars: Int = 16_000,
    val maxOpenTabs: Int = 12,
    val maxDiagnostics: Int = 25,
    val terminalLines: Int = 80,
    val includeCleanGitStatus: Boolean = false,
)
