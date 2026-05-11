package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.GeminiIdeService

class OpenDiffTool : McpTool {
    override fun getName(): String = "openDiff"
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val newContent = args.get("newContent")?.asString.orEmpty()
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        val file = ideService.resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace")
        val oldContent = ideService.getFileContent(file.absolutePath) ?: runCatching { file.readText() }.getOrDefault("")
        
        ideService.showPatch(file.absolutePath, oldContent, newContent, "Review Gemini file change") {
            ideService.writeFile(file, newContent)
            ideService.refreshEditors(force = false)
        }
        
        return textResult("Review opened in Xed Editor for ${file.absolutePath}. Results will be sent via notifications.")
    }
}

class CloseDiffTool : McpTool {
    override fun getName(): String = "closeDiff"
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val file = ideService.resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace")
        val content = ideService.getFileContent(file.absolutePath) ?: runCatching { file.readText() }.getOrDefault("")
        return JsonObject().apply { addProperty("content", content) }.let { jsonResult(it) }
    }
}
