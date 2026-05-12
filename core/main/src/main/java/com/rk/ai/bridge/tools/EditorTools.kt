package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GetOpenFilesTool : BaseMcpTool() {
    override fun getName(): String = "getOpenFiles"
    override fun getDescription(): String = "Returns metadata about all open editor tabs."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        return textResult(ideService.getOpenFiles().toString())
    }
}

class GetActiveFileTool : BaseMcpTool() {
    override fun getName(): String = "getActiveFile"
    override fun getDescription(): String = "Gets the active editor tab info including file path and content."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val activeFile = ideService.getActiveFile()
        return textResult(activeFile?.toString() ?: "{}")
    }
}

class GetSelectionTool : BaseMcpTool() {
    override fun getName(): String = "getSelection"
    override fun getDescription(): String = "Returns the currently selected text in the active editor."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        return textResult(ideService.getSelection())
    }
}

class ReplaceSelectionTool : BaseMcpTool() {
    override fun getName(): String = "replaceSelection"
    override fun getDescription(): String = "Replaces the current selection with new content after user review."
    override fun getRequiredParams(): Map<String, String> = mapOf("newContent" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val newContent = requireString(args, "newContent")
        ideService.replaceSelection(newContent)
        return textResult("Replacement opened in Xed for user review. Results will be sent via notifications.")
    }
}

class InsertAtCursorTool : BaseMcpTool() {
    override fun getName(): String = "insertAtCursor"
    override fun getDescription(): String = "Inserts text at the current cursor position after user review."
    override fun getRequiredParams(): Map<String, String> = mapOf("newContent" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val newContent = requireString(args, "newContent")
        ideService.insertAtCursor(newContent)
        return textResult("Insertion opened in Xed for user review. Results will be sent via notifications.")
    }
}

class SaveOpenFilesTool : BaseMcpTool() {
    override fun getName(): String = "saveOpenFiles"
    override fun getDescription(): String = "Saves all dirty open editor tabs."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        return textResult(ideService.saveAllFiles())
    }
}

class RefreshOpenEditorsTool : BaseMcpTool() {
    override fun getName(): String = "refreshOpenEditors"
    override fun getDescription(): String = "Refreshes all non-dirty open editor tabs from disk."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        ideService.refreshEditors(filePath = null, force = false)
        return textResult("refreshed non-dirty open editor tabs")
    }
}

class RefreshFileTool : BaseMcpTool() {
    override fun getName(): String = "refreshFile"
    override fun getDescription(): String = "Refreshes a specific editor tab from disk."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(ideService, filePath)
        ideService.refreshEditors(filePath = file.absolutePath, force = false)
        return textResult("refreshed ${file.absolutePath}")
    }
}
