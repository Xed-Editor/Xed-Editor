package com.rk.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal object IdeMcpTools {
    fun list(): JsonArray =
        JsonArray().apply {
            add(schema("getIdeInfo", "Returns IDE bridge status, version, workspace path, connected clients count, and available tools count. Useful for verifying the integration is alive.", emptyList(), emptyMap()))
            add(schema("openDiff", "Opens a side-by-side diff view in Xed-Editor comparing old vs new file content. The user must review and approve/reject the change before it is applied.", listOf("filePath", "newContent"), mapOf("filePath" to "string", "newContent" to "string")))
            add(schema("closeDiff", "Closes an open diff view for a file and returns the final content after user review/edits.", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("readFile", "Reads the full content of a file at the given path. Prefers the content from an open editor tab if available, otherwise reads from disk.", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("listFiles", "Lists files and directories in a directory. Supports recursive listing with maxFiles limit (default 500, max 5000).", listOf("directoryPath"), mapOf("directoryPath" to "string", "recursive" to "boolean", "maxFiles" to "number")))
            add(schema("getOpenFiles", "Returns a list of all currently open editor tabs with their paths and metadata.", emptyList(), emptyMap()))
            add(schema("getActiveFile", "Returns the active editor tab info including file path, cursor position, text selection, and full file content.", emptyList(), emptyMap()))
            add(schema("getSelection", "Returns the currently selected text in the active editor, or empty string if nothing is selected.", emptyList(), emptyMap()))
            add(schema("replaceSelection", "Opens a diff review for replacing the current selection (or full file) with new content. User must approve the change.", listOf("newContent"), mapOf("newContent" to "string")))
            add(schema("insertAtCursor", "Opens a diff review for inserting text at the current cursor position. User must approve the insertion.", listOf("newContent"), mapOf("newContent" to "string")))
            add(schema("openFile", "Opens a workspace file in the Xed-Editor editor tab and makes it the active focused tab.", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("writeFile", "Opens a diff review for writing new content to a file. Shows before/after comparison for user approval. If the file is open in an editor tab, it will be refreshed.", listOf("filePath", "content"), mapOf("filePath" to "string", "content" to "string")))
            add(schema("saveOpenFiles", "Saves all dirty (unsaved) open editor tabs to disk. Returns a summary of save results.", emptyList(), emptyMap()))
            add(schema("refreshOpenEditors", "Refreshes all non-dirty open editor tabs from disk, useful when files changed externally.", emptyList(), emptyMap()))
            add(schema("showMessage", "Displays a short toast notification message in the Xed-Editor UI.", listOf("message"), mapOf("message" to "string")))
            add(schema("runCommand", "Runs a shell command in the Xed-Editor terminal environment and returns combined stdout+stderr output with exit code.", listOf("command"), mapOf("command" to "string", "timeoutSeconds" to "number")))
            add(schema("refreshFile", "Refreshes a specific editor tab from disk if it is open and not dirty.", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("searchCode", "Searches for text/code patterns project-wide across all files. Returns matching file paths and line numbers.", listOf("query"), mapOf("query" to "string", "limit" to "number")))
            add(schema("findFiles", "Finds files by name pattern project-wide (e.g. '*.kt' or 'build.gradle'). Returns matching file paths.", listOf("query"), mapOf("query" to "string", "limit" to "number")))
            add(schema("getDiagnostics", "Returns LSP diagnostics (errors, warnings, hints) for a file. Requires LSP server to be running for the file type.", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("findDefinitions", "Finds the definition location (file, line, column) of a symbol at a given position using LSP.", listOf("filePath", "line", "column"), mapOf("filePath" to "string", "line" to "number", "column" to "number")))
            add(schema("findReferences", "Finds all references/usage locations of a symbol at a given position across the project using LSP.", listOf("filePath", "line", "column"), mapOf("filePath" to "string", "line" to "number", "column" to "number")))
            add(schema("renameSymbol", "Renames a symbol at a given position across all files in the project using LSP rename. User must review the changes.", listOf("filePath", "line", "column", "newName"), mapOf("filePath" to "string", "line" to "number", "column" to "number", "newName" to "string")))
            add(schema("formatDocument", "Formats a document using the LSP formatter. Requires LSP server that supports formatting for the file type.", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("getGitStatus", "Returns the git working tree status for the workspace repository: current branch, staged/unstaged changes, untracked files.", emptyList(), mapOf("path" to "string")))
            add(schema("createFile", "Creates a new file at the specified path with optional initial text content. Fails if the file already exists.", listOf("filePath"), mapOf("filePath" to "string", "content" to "string")))
            add(schema("deleteFile", "Permanently deletes a file (not directories) from the workspace. Closes any open editor tab for the file first.", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("renameFile", "Renames or moves a file from sourcePath to destPath within the workspace. Closes the old editor tab if open.", listOf("sourcePath", "destPath"), mapOf("sourcePath" to "string", "destPath" to "string")))
            add(schema("getTerminalOutput", "Gets recent terminal transcript output from the currently active AI agent terminal session (Gemini or OpenCode).", emptyList(), mapOf("lines" to "number")))
            add(schema("getProjectStructure", "Returns a hierarchical tree view of the project directory. Useful for understanding project layout. Ignores common build artifacts and hidden dirs.", listOf("path"), mapOf("path" to "string", "maxDepth" to "number", "maxItems" to "number")))
            add(schema("getSymbolUnderCursor", "Returns the symbol (function name, class name, variable name) under the cursor in the active editor with surrounding context.", emptyList(), emptyMap()))
            add(schema("getProjectConfig", "Detects and returns project configuration: programming language, frameworks, build system used by the project.", emptyList(), mapOf("path" to "string")))
            add(schema("getGitDiff", "Returns the unstaged git diff for the workspace repository showing uncommitted changes.", emptyList(), mapOf("path" to "string")))
        }

    private fun schema(
        name: String,
        description: String,
        required: List<String>,
        properties: Map<String, String>,
    ): JsonObject =
        JsonObject().apply {
            addProperty("name", name)
            addProperty("description", description)
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    properties.forEach { (propertyName, type) ->
                        add(propertyName, JsonObject().apply { addProperty("type", type) })
                    }
                })
                add("required", JsonArray().apply { required.forEach { add(it) } })
            })
        }
}
