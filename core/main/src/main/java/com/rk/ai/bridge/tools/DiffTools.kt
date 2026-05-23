package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class OpenDiffTool : BaseMcpTool() {
    override val name: String = "openDiff"
    override val description: String = "Opens a side-by-side diff view for user review."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string", "newContent" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val newContent = requireString(args, "newContent")
        val file = safeResolvePath(context, filePath)
        val msg = showPatchAndApply(context, file, newContent, "Review Gemini file change", refreshAfterApply = true)
        return resultText(msg)
    }
}

class GetDiffResultTool : BaseMcpTool() {
    override val name: String = "getDiffResult"
    override val description: String = "Returns the current file content after a diff review."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val file = safeResolvePath(context, filePath)
        val content = context.ideService.getFileContent(file.absolutePath) ?: runCatching { file.readText() }.getOrDefault("")
        return resultText(content)
    }
}

class RejectDiffTool : BaseMcpTool() {
    override val name: String = "rejectDiff"
    override val description: String = "Rejects a pending diff/patch for a file."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val file = safeResolvePath(context, filePath)
        context.ideService.rejectPatch(file.absolutePath)
        return resultText("Rejected patch for ${file.absolutePath}")
    }
}
