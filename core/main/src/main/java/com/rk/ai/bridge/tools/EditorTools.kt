package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.GeminiIdeService

class GetOpenFilesTool : McpTool {
    override fun getName(): String = "getOpenFiles"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
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
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
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
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        return textResult(ideService.getSelection())
    }
}

class ReplaceSelectionTool : McpTool {
    override fun getName(): String = "replaceSelection"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val newContent = args.get("newContent")?.asString.orEmpty()
        ideService.replaceSelection(newContent)
        return textResult("Replacement opened in Xed for user review. Results will be sent via notifications.")
    }
}

class InsertAtCursorTool : McpTool {
    override fun getName(): String = "insertAtCursor"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val newContent = args.get("newContent")?.asString.orEmpty()
        ideService.insertAtCursor(newContent)
        return textResult("Insertion opened in Xed for user review. Results will be sent via notifications.")
    }
}

class SaveOpenFilesTool : McpTool {
    override fun getName(): String = "saveOpenFiles"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        return textResult(ideService.saveAll())
    }
}

class RefreshOpenEditorsTool : McpTool {
    override fun getName(): String = "refreshOpenEditors"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        ideService.refreshEditors(filePath = null, force = false)
        return textResult("refreshed non-dirty open editor tabs")
    }
}

class RefreshFileTool : McpTool {
    override fun getName(): String = "refreshFile"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val file = ideService.resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace")
        ideService.refreshEditors(filePath = file.absolutePath, force = false)
        return textResult("refreshed ${file.absolutePath}")
    }
}
