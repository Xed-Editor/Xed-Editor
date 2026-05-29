package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class OpenDiffTool : BaseMcpTool() {
    override fun getCategory(): String = "Diff"
    override fun getName(): String = "openDiff"
    override fun getDescription(): String = "Opens a side-by-side diff view for user review."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "newContent" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file",
        "newContent" to "Proposed new content to diff against"
    )
    override fun getBlankRequiredParams(): Set<String> = setOf("newContent")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val newContent = requireString(args, "newContent", allowBlank = true)
        val file = resolvePathOrThrow(context, filePath)
        val msg = showPatchAndApply(context.ideService, file, newContent, "Review Gemini file change", refreshAfterApply = true)
        return McpToolResult.success(msg)
    }
}

class GetDiffResultTool : BaseMcpTool() {
    override fun getCategory(): String = "Diff"
    override fun getName(): String = "getDiffResult"
    override fun getDescription(): String = "Returns the current file content after a diff review."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(context, filePath)
        val content = context.ideService.getFileContent(file.absolutePath) ?: runCatching { file.readText() }.getOrDefault("")
        return McpToolResult.success(content)
    }
}

class RejectDiffTool : BaseMcpTool() {
    override fun getCategory(): String = "Diff"
    override fun getName(): String = "rejectDiff"
    override fun getDescription(): String = "Rejects a pending diff/patch for a file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file with the pending diff"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(context, filePath)
        context.ideService.rejectPatch(file.absolutePath)
        return McpToolResult.success("Rejected patch for ${file.absolutePath}")
    }
}
