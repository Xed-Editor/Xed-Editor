package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class GetOpenFilesTool : BaseMcpTool() {
    override val name: String = "getOpenFiles"
    override val description: String = "Lists all files currently open in editor tabs. Use this to understand the user's current working set."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        return resultText(context.ideService.getOpenFiles().toString())
    }
}

class GetActiveFileTool : BaseMcpTool() {
    override val name: String = "getActiveFile"
    override val description: String = "Gets the path and full content of the file currently visible in the editor. Use this to focus on what the user is currently looking at."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val activeFile = context.ideService.getActiveFile()
        return resultText(activeFile?.toString() ?: "{}")
    }
}

class GetSelectionTool : BaseMcpTool() {
    override val name: String = "getSelection"
    override val description: String = "Returns the text currently selected by the user in the active editor. Use this to perform actions on a specific block of code the user has highlighted."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        return resultText(context.ideService.getSelection())
    }
}

class ReplaceSelectionTool : BaseMcpTool() {
    override val name: String = "replaceSelection"
    override val description: String = "Replaces the user's current selection with new text. Use this for surgical edits within the active file. Opens a review tab for the user."
    override val requiredParams: Map<String, String> = mapOf("newContent" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val newContent = requireString(args, "newContent")
        context.ideService.replaceSelection(newContent)
        return resultText("Replacement opened in Xed for user review. Results will be sent via notifications.")
    }
}

class InsertAtCursorTool : BaseMcpTool() {
    override val name: String = "insertAtCursor"
    override val description: String = "Inserts text at the user's current cursor position. Useful for adding new code without replacing anything. Opens a review tab for the user."
    override val requiredParams: Map<String, String> = mapOf("newContent" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val newContent = requireString(args, "newContent")
        context.ideService.insertAtCursor(newContent)
        return resultText("Insertion opened in Xed for user review. Results will be sent via notifications.")
    }
}

class SaveOpenFilesTool : BaseMcpTool() {
    override val name: String = "saveOpenFiles"
    override val description: String = "Saves all unsaved changes in all open editor tabs. Recommended before running external commands or terminal tasks."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        return resultText(context.ideService.saveAllFiles())
    }
}

class RefreshOpenEditorsTool : BaseMcpTool() {
    override val name: String = "refreshOpenEditors"
    override val description: String = "Refreshes all non-dirty open editor tabs from disk."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        context.ideService.refreshEditors(filePath = null, force = false)
        return resultText("refreshed non-dirty open editor tabs")
    }
}

class RefreshFileTool : BaseMcpTool() {
    override val name: String = "refreshFile"
    override val description: String = "Refreshes a specific editor tab from disk."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val file = safeResolvePath(context, filePath)
        context.ideService.refreshEditors(filePath = file.absolutePath, force = false)
        return resultText("refreshed ${file.absolutePath}")
    }
}
