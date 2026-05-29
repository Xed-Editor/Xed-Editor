package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class GetOpenFilesTool : BaseMcpTool() {
    override fun getCategory(): String = "Editor"
    override fun getName(): String = "getOpenFiles"
    override fun getDescription(): String = "Lists all files currently open in editor tabs. Use this to understand the user's current working set."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        return McpToolResult.success(context.ideService.getOpenFiles().toString())
    }
}

class GetActiveFileTool : BaseMcpTool() {
    override fun getCategory(): String = "Editor"
    override fun getName(): String = "getActiveFile"
    override fun getDescription(): String = "Gets the path and full content of the file currently visible in the editor."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val activeFile = context.ideService.getActiveFile()
        return McpToolResult.success(activeFile?.toString() ?: "{}")
    }
}

class GetSelectionTool : BaseMcpTool() {
    override fun getCategory(): String = "Editor"
    override fun getName(): String = "getSelection"
    override fun getDescription(): String = "Returns the text currently selected by the user in the active editor."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        return McpToolResult.success(context.ideService.getSelection())
    }
}

class ReplaceSelectionTool : BaseMcpTool() {
    override fun getCategory(): String = "Editor"
    override fun getName(): String = "replaceSelection"
    override fun getDescription(): String = "Replaces the user's current selection with new text. Opens a review tab for the user."
    override fun getRequiredParams(): Map<String, String> = mapOf("newContent" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "newContent" to "Text to replace the selection with"
    )
    override fun getBlankRequiredParams(): Set<String> = setOf("newContent")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val newContent = requireString(args, "newContent", allowBlank = true)
        context.ideService.replaceSelection(newContent)
        return McpToolResult.success("Replacement opened in Xed for user review. Results will be sent via notifications.")
    }
}

class InsertAtCursorTool : BaseMcpTool() {
    override fun getCategory(): String = "Editor"
    override fun getName(): String = "insertAtCursor"
    override fun getDescription(): String = "Inserts text at the user's current cursor position. Opens a review tab for the user."
    override fun getRequiredParams(): Map<String, String> = mapOf("newContent" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "newContent" to "Text to insert at the cursor"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val newContent = requireString(args, "newContent")
        context.ideService.insertAtCursor(newContent)
        return McpToolResult.success("Insertion opened in Xed for user review. Results will be sent via notifications.")
    }
}

class SaveOpenFilesTool : BaseMcpTool() {
    override fun getCategory(): String = "Editor"
    override fun getName(): String = "saveOpenFiles"
    override fun getDescription(): String = "Saves all unsaved changes in all open editor tabs. Recommended before running external commands."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        return McpToolResult.success(context.ideService.saveAllFiles())
    }
}

class RefreshOpenEditorsTool : BaseMcpTool() {
    override fun getCategory(): String = "Editor"
    override fun getName(): String = "refreshOpenEditors"
    override fun getDescription(): String = "Refreshes all non-dirty open editor tabs from disk."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        context.ideService.refreshEditors(filePath = null, force = false)
        return McpToolResult.success("refreshed non-dirty open editor tabs")
    }
}

class RefreshFileTool : BaseMcpTool() {
    override fun getCategory(): String = "Editor"
    override fun getName(): String = "refreshFile"
    override fun getDescription(): String = "Refreshes a specific editor tab from disk."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path of the file to refresh"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(context, filePath)
        context.ideService.refreshEditors(filePath = file.absolutePath, force = false)
        return McpToolResult.success("refreshed ${file.absolutePath}")
    }
}
