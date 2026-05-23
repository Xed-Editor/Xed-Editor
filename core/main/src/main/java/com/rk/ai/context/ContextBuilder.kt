package com.rk.ai.context

import android.util.Log
import com.rk.ai.IdeBridge
import com.rk.tabs.editor.EditorTab

data class IdeContext(
    val currentFile: String = "",
    val selectedText: String = "",
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
    val projectRoot: String = "",
    val openFiles: List<String> = emptyList(),
    val workspaceStructure: String = "",
    val gitDiff: String = "",
    val diagnostics: List<DiagnosticEntry> = emptyList(),
    val language: String = "",
    val fileExtension: String = "",
    val lineCount: Int = 0,
)

data class DiagnosticEntry(
    val file: String,
    val line: Int,
    val message: String,
    val severity: String,
)

object ContextBuilder {
    private const val TAG = "ContextBuilder"

    fun buildFileContext(editorTab: EditorTab?): FileContext {
        if (editorTab == null) return FileContext()

        val editor = editorTab.editorState.editor.get()
        val file = editorTab.file
        val fileName = file?.getName() ?: ""
        val filePath = file?.getAbsolutePath() ?: ""
        val extension = fileName.substringAfterLast('.', "")
        val selectedText = editor?.getSelectedText().orEmpty()
        val cursorLine = (editor?.cursor?.leftLine ?: 0).coerceAtLeast(0)
        val cursorColumn = (editor?.cursor?.leftColumn ?: 0).coerceAtLeast(0)
        val lineCount = editor?.lineCount ?: 0
        val fullText = editor?.text?.toString().orEmpty()

        return FileContext(
            fileName = fileName,
            filePath = filePath,
            extension = extension,
            selectedText = selectedText,
            cursorLine = cursorLine,
            cursorColumn = cursorColumn,
            lineCount = lineCount,
            fullText = fullText,
            language = editorTab.editorState.textmateScope ?: "",
        )
    }

    fun buildSystemPrompt(context: IdeContext): String = buildString {
        appendLine("You are Xed-Editor's AI coding assistant integrated into an Android IDE.")
        appendLine("You help users write, debug, refactor, and understand code.")
        appendLine()
        appendLine("## Current Context")
        if (context.currentFile.isNotBlank()) {
            appendLine("- File: `${context.currentFile}`")
            if (context.language.isNotBlank()) appendLine("- Language: ${context.language}")
            appendLine("- Lines: ${context.lineCount}")
            appendLine("- Cursor: ${context.cursorLine + 1}:${context.cursorColumn + 1}")
        }
        if (context.selectedText.isNotBlank()) {
            appendLine()
            appendLine("### Selected Code")
            appendLine("```${context.fileExtension.lowercase()}")
            appendLine(context.selectedText.take(2000))
            appendLine("```")
        }
        if (context.diagnostics.isNotEmpty()) {
            appendLine()
            appendLine("### Diagnostics")
            context.diagnostics.take(10).forEach { d ->
                appendLine("- ${d.severity}: ${d.file}:${d.line}: ${d.message.take(200)}")
            }
        }
        appendLine()
        appendLine("When suggesting code changes:")
        appendLine("- Explain what you changed and why")
        appendLine("- Use markdown formatting")
        appendLine("- Specify the language in code fences")
        appendLine("- Be concise and direct")
    }

    fun buildFullPrompt(userPrompt: String, context: IdeContext): String = buildString {
        appendLine(buildSystemPrompt(context))
        appendLine()
        appendLine("## Request")
        appendLine(userPrompt)
    }

    fun buildWorkspaceContext(projectRoot: String, openFiles: List<String>): String {
        if (projectRoot.isBlank()) return ""
        return buildString {
            appendLine("### Workspace")
            appendLine("- Project: ${projectRoot.split("/").lastOrNull() ?: projectRoot}")
            if (openFiles.isNotEmpty()) {
                appendLine("- Open files:")
                openFiles.take(10).forEach { f ->
                    appendLine("  - $f")
                }
            }
        }
    }
}

data class FileContext(
    val fileName: String = "",
    val filePath: String = "",
    val extension: String = "",
    val selectedText: String = "",
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
    val lineCount: Int = 0,
    val fullText: String = "",
    val language: String = "",
)
