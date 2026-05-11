package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.GeminiIdeService

class GetOpenFilesTool : McpTool {
    override fun getName(): String = "getOpenFiles"
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
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
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
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
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        return textResult(ideService.getSelection())
    }
}

class ReplaceSelectionTool : McpTool {
    override fun getName(): String = "replaceSelection"
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val newContent = args.get("newContent")?.asString.orEmpty()
        return textResult(ideService.replaceSelection(newContent))
    }
}

class InsertAtCursorTool : McpTool {
    override fun getName(): String = "insertAtCursor"
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val newContent = args.get("newContent")?.asString.orEmpty()
        return textResult(ideService.insertAtCursor(newContent))
    }
}

class SaveOpenFilesTool : McpTool {
    override fun getName(): String = "saveOpenFiles"
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        return textResult(ideService.saveAll())
    }
}

class RefreshOpenEditorsTool : McpTool {
    override fun getName(): String = "refreshOpenEditors"
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        ideService.refreshEditors(filePath = null, force = false)
        return textResult("refreshed non-dirty open editor tabs")
    }
}

class RefreshFileTool : McpTool {
    override fun getName(): String = "refreshFile"
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val file = ideService.resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace")
        ideService.refreshEditors(filePath = file.absolutePath, force = false)
        return textResult("refreshed ${file.absolutePath}")
    }
}
