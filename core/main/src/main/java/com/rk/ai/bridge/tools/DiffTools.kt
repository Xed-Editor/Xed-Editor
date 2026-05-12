package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class OpenDiffTool : BaseMcpTool() {
    override fun getName(): String = "openDiff"
    override fun getDescription(): String = "Opens a side-by-side diff view for user review."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "newContent" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val newContent = requireString(args, "newContent")
        val file = resolvePathOrThrow(ideService, filePath)
        val msg = showPatchAndApply(ideService, file, newContent, "Review Gemini file change", refreshAfterApply = true)
        return textResult(msg)
    }
}

class GetDiffResultTool : BaseMcpTool() {
    override fun getName(): String = "getDiffResult"
    override fun getDescription(): String = "Returns the current file content after a diff review."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(ideService, filePath)
        val content = ideService.getFileContent(file.absolutePath) ?: runCatching { file.readText() }.getOrDefault("")
        return textResult(content)
    }
}

class RejectDiffTool : BaseMcpTool() {
    override fun getName(): String = "rejectDiff"
    override fun getDescription(): String = "Rejects a pending diff/patch for a file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(ideService, filePath)
        ideService.rejectPatch(file.absolutePath)
        return textResult("Rejected patch for ${file.absolutePath}")
    }
}
