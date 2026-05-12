package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService

class OpenDiffTool : McpTool {
    override fun getName(): String = "openDiff"
    override fun getDescription(): String = "Opens a side-by-side diff view for user review."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "newContent" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
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

class GetDiffResultTool : McpTool {
    override fun getName(): String = "getDiffResult"
    override fun getDescription(): String = "Returns the current file content after a diff review."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val file = ideService.resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace")
        val content = ideService.getFileContent(file.absolutePath) ?: runCatching { file.readText() }.getOrDefault("")
        return JsonObject().apply { addProperty("content", content) }.let { jsonResult(it) }
    }
}

class RejectDiffTool : McpTool {
    override fun getName(): String = "rejectDiff"
    override fun getDescription(): String = "Rejects a pending diff/patch for a file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        val file = ideService.resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace")
        ideService.rejectPatch(file.absolutePath)
        return textResult("Rejected patch for ${file.absolutePath}")
    }
}
