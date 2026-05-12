package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService

class GetOpenFilesTool : McpTool {
    override fun getName(): String = "getOpenFiles"
    override fun getDescription(): String = "Returns metadata about all open editor tabs."
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val openFiles = ideService.getOpenFiles()
        return JsonObject().apply {
            add("content", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", openFiles.toString())
                })
            })
        }
    }
}

class GetActiveFileTool : McpTool {
    override fun getName(): String = "getActiveFile"
    override fun getDescription(): String = "Gets the active editor tab info including file path and content."
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val activeFile = ideService.getActiveFile()
        return if (activeFile != null) {
            textResult(activeFile.toString())
        } else {
            textResult("{}")
        }
    }
}

class GetSelectionTool : McpTool {
    override fun getName(): String = "getSelection"
    override fun getDescription(): String = "Returns the currently selected text in the active editor."
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        return textResult(ideService.getSelection())
    }
}

class ReplaceSelectionTool : McpTool {
    override fun getName(): String = "replaceSelection"
    override fun getDescription(): String = "Replaces the current selection with new content after user review."
    override fun getRequiredParams(): Map<String, String> = mapOf("newContent" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val newContent = args.get("newContent")?.asString.orEmpty()
        ideService.replaceSelection(newContent)
        return textResult("Replacement opened in Xed for user review. Results will be sent via notifications.")
    }
}

class InsertAtCursorTool : McpTool {
    override fun getName(): String = "insertAtCursor"
    override fun getDescription(): String = "Inserts text at the current cursor position after user review."
    override fun getRequiredParams(): Map<String, String> = mapOf("newContent" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val newContent = args.get("newContent")?.asString.orEmpty()
        ideService.insertAtCursor(newContent)
        return textResult("Insertion opened in Xed for user review. Results will be sent via notifications.")
    }
}

class SaveOpenFilesTool : McpTool {
    override fun getName(): String = "saveOpenFiles"
    override fun getDescription(): String = "Saves all dirty open editor tabs."
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        return textResult(ideService.saveAllFiles())
    }
}

class RefreshOpenEditorsTool : McpTool {
    override fun getName(): String = "refreshOpenEditors"
    override fun getDescription(): String = "Refreshes all non-dirty open editor tabs from disk."
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        ideService.refreshEditors(filePath = null, force = false)
        return textResult("refreshed non-dirty open editor tabs")
    }
}

class RefreshFileTool : McpTool {
    override fun getName(): String = "refreshFile"
    override fun getDescription(): String = "Refreshes a specific editor tab from disk."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val file = ideService.resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace")
        ideService.refreshEditors(filePath = file.absolutePath, force = false)
        return textResult("refreshed ${file.absolutePath}")
    }
}
