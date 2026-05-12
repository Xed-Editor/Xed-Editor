package com.rk.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal object IdeMcpTools {
    fun list(): JsonArray =
        JsonArray().apply {
            add(schema("getIdeInfo", "Get IDE information: bridge status, version, workspace, connected clients", emptyList(), emptyMap()))
            add(schema("openDiff", "Open a proposed file replacement in Xed-Editor for user review before writing", listOf("filePath", "newContent"), mapOf("filePath" to "string", "newContent" to "string")))
            add(schema("closeDiff", "Close a diff and return final file content", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("readFile", "Read the content of a file (prefers open editor content)", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("listFiles", "List files in a directory. Supports recursive and maxFiles.", listOf("directoryPath"), mapOf("directoryPath" to "string", "recursive" to "boolean", "maxFiles" to "number")))
            add(schema("getOpenFiles", "Return currently open editor files", emptyList(), emptyMap()))
            add(schema("getActiveFile", "Return active editor file, cursor, selection, and content", emptyList(), emptyMap()))
            add(schema("getSelection", "Return selected text in the active editor", emptyList(), emptyMap()))
            add(schema("replaceSelection", "Replace selected text in the active editor after user review", listOf("newContent"), mapOf("newContent" to "string")))
            add(schema("insertAtCursor", "Insert text at the active editor cursor after user review", listOf("newContent"), mapOf("newContent" to "string")))
            add(schema("openFile", "Open a workspace file in Xed and make it active", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("writeFile", "Write a workspace file and immediately refresh the matching open Xed editor tab", listOf("filePath", "content"), mapOf("filePath" to "string", "content" to "string")))
            add(schema("saveOpenFiles", "Save all dirty open Xed editor tabs to disk before reading/editing files", emptyList(), emptyMap()))
            add(schema("refreshOpenEditors", "Refresh all non-dirty open Xed editor tabs from disk after file edits", emptyList(), emptyMap()))
            add(schema("showMessage", "Show a short message in Xed", listOf("message"), mapOf("message" to "string")))
            add(schema("runCommand", "Run a shell command in the workspace and return stdout/stderr", listOf("command"), mapOf("command" to "string", "timeoutSeconds" to "number")))
            add(schema("refreshFile", "Refresh an open editor tab from disk", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("searchCode", "Search for code project-wide", listOf("query"), mapOf("query" to "string", "limit" to "number")))
            add(schema("findFiles", "Find files by name project-wide", listOf("query"), mapOf("query" to "string", "limit" to "number")))
            add(schema("getDiagnostics", "Get LSP diagnostics (errors/warnings) for a file", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("findDefinitions", "Find definitions for a symbol at a position", listOf("filePath", "line", "column"), mapOf("filePath" to "string", "line" to "number", "column" to "number")))
            add(schema("findReferences", "Find references for a symbol at a position", listOf("filePath", "line", "column"), mapOf("filePath" to "string", "line" to "number", "column" to "number")))
            add(schema("renameSymbol", "Rename a symbol at a position project-wide", listOf("filePath", "line", "column", "newName"), mapOf("filePath" to "string", "line" to "number", "column" to "number", "newName" to "string")))
            add(schema("formatDocument", "Format a document using the LSP", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("getGitStatus", "Get git status of the workspace repository (branch, changes, ahead/behind)", emptyList(), mapOf("path" to "string")))
            add(schema("createFile", "Create a new file at the specified path with optional initial content", listOf("filePath"), mapOf("filePath" to "string", "content" to "string")))
            add(schema("deleteFile", "Permanently delete a file or empty directory from the workspace", listOf("filePath"), mapOf("filePath" to "string")))
            add(schema("getTerminalOutput", "Get recent output from the running Gemini CLI terminal session", emptyList(), mapOf("lines" to "number")))
            add(schema("getProjectStructure", "Get a high-level directory tree of the project, showing folders and files up to a configurable depth", listOf("path"), mapOf("path" to "string", "maxDepth" to "number", "maxItems" to "number")))
            add(schema("getSymbolUnderCursor", "Get the symbol (function/class/variable name) under the editor cursor with surrounding context", emptyList(), emptyMap()))
            add(schema("getProjectConfig", "Detect project language, framework, build system from workspace files", emptyList(), mapOf("path" to "string")))
            add(schema("getGitDiff", "Get the unstaged git diff for the workspace repository", emptyList(), mapOf("path" to "string")))
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
