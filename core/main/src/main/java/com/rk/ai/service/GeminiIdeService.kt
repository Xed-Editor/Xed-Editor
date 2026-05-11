package com.rk.ai.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.tabs.editor.EditorTab
import java.io.File

interface GeminiIdeService {
    /** Resolves a path (absolute or relative to active editor) to a File in the workspace. */
    fun resolvePath(path: String): File?

    /** Lists files in a directory within the workspace. */
    fun listFiles(directory: File, recursive: Boolean, maxFiles: Int): List<String>

    /** Gets the text content of a file, preferring an open editor tab if available. */
    suspend fun getFileContent(filePath: String): String?

    /** Shows a patch for user review. Non-blocking. */
    fun showPatch(
        filePath: String,
        oldContent: String,
        newContent: String,
        title: String = "Review AI change",
        onApply: suspend () -> Unit
    )

    /** Rejects a pending patch for the given file path. */
    fun rejectPatch(filePath: String)

    /** Directly writes text to a file and refreshes any associated editor tab. */
    suspend fun writeFile(file: File, content: String)

    /** Refreshes one or more editor tabs from disk. */
    fun refreshEditors(filePath: String? = null, force: Boolean = false)

    /** Opens a file in the editor. */
    fun openFile(file: File)

    /** Gets metadata about all open editor tabs. */
    suspend fun getOpenFiles(): List<JsonObject>

    /** Gets metadata and content for the currently active editor tab. */
    suspend fun getActiveFile(): JsonObject?

    /** Gets the currently selected text in the active editor. */
    suspend fun getSelection(): String

    /** Replaces the current selection (or entire file) with new content, after user review. Non-blocking. */
    fun replaceSelection(newContent: String)

    /** Inserts text at the current cursor position, after user review. Non-blocking. */
    fun insertAtCursor(newContent: String)

    /** Saves all dirty editor tabs in parallel. */
    suspend fun saveAll(): String

    /** Ensures the Gemini CLI configuration has IDE integration enabled. */
    fun ensureIdeEnabled()

    /** Searches for code project-wide. */
    suspend fun searchCode(query: String, limit: Int = 100): JsonArray

    /** Finds files by name project-wide. */
    suspend fun findFiles(query: String, limit: Int = 100): JsonArray

    /** Shows a message (toast) to the user. */
    fun showMessage(message: String)

    /** Runs a shell command in the Ubuntu environment. */
    suspend fun runCommand(command: String, timeoutSeconds: Long): CommandResult

    /** Gets the primary workspace root path. */
    fun getPrimaryWorkspacePath(): String

    /** Gets LSP diagnostics for a file. */
    suspend fun getDiagnostics(filePath: String): JsonArray

    /** Finds definitions for a symbol at a position. */
    suspend fun findDefinitions(filePath: String, line: Int, column: Int): JsonArray

    /** Finds references for a symbol at a position. */
    suspend fun findReferences(filePath: String, line: Int, column: Int): JsonArray

    /** Renames a symbol at a position project-wide (after user review). */
    fun renameSymbol(filePath: String, line: Int, column: Int, newName: String)

    /** Formats a document using the LSP. */
    suspend fun formatDocument(filePath: String)
}

data class CommandResult(
    val output: String,
    val error: String,
    val exitCode: Int,
    val timedOut: Boolean
)
