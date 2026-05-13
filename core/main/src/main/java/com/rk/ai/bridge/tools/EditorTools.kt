package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GetOpenFilesTool : BaseMcpTool() {
    override fun getName(): String = "getOpenFiles"
    override fun getDescription(): String = "Lists all files currently open in editor tabs. Use this to understand the user's current working set."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        return textResult(ideService.getOpenFiles().toString())
    }
}

class GetActiveFileTool : BaseMcpTool() {
    override fun getName(): String = "getActiveFile"
    override fun getDescription(): String = "Gets the path and full content of the file currently visible in the editor. Use this to focus on what the user is currently looking at."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val activeFile = ideService.getActiveFile()
        return textResult(activeFile?.toString() ?: "{}")
    }
}

class GetSelectionTool : BaseMcpTool() {
    override fun getName(): String = "getSelection"
    override fun getDescription(): String = "Returns the text currently selected by the user in the active editor. Use this to perform actions on a specific block of code the user has highlighted."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        return textResult(ideService.getSelection())
    }
}

class ReplaceSelectionTool : BaseMcpTool() {
    override fun getName(): String = "replaceSelection"
    override fun getDescription(): String = "Replaces the user's current selection with new text. Use this for surgical edits within the active file. Opens a review tab for the user."
    override fun getRequiredParams(): Map<String, String> = mapOf("newContent" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "newContent" to "Text to replace the selection with"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val newContent = requireString(args, "newContent")
        ideService.replaceSelection(newContent)
        return textResult("Replacement opened in Xed for user review. Results will be sent via notifications.")
    }
}

class InsertAtCursorTool : BaseMcpTool() {
    override fun getName(): String = "insertAtCursor"
    override fun getDescription(): String = "Inserts text at the user's current cursor position. Useful for adding new code without replacing anything. Opens a review tab for the user."
    override fun getRequiredParams(): Map<String, String> = mapOf("newContent" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "newContent" to "Text to insert at the cursor"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val newContent = requireString(args, "newContent")
        ideService.insertAtCursor(newContent)
        return textResult("Insertion opened in Xed for user review. Results will be sent via notifications.")
    }
}

class SaveOpenFilesTool : BaseMcpTool() {
    override fun getName(): String = "saveOpenFiles"
    override fun getDescription(): String = "Saves all unsaved changes in all open editor tabs. Recommended before running external commands or terminal tasks."
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
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path of the file to refresh"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(ideService, filePath)
        ideService.refreshEditors(filePath = file.absolutePath, force = false)
        return textResult("refreshed ${file.absolutePath}")
    }
}