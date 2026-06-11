package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class ApplyBatchEditsTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "applyBatchEdits"
    override fun getDescription(): String = "RECOMMENDED: Applies multiple file changes at once. ALWAYS use this for cross-file refactorings to ensure consistency and minimize turns. Takes a JSON object where keys are absolute file paths and values are new content."
    override fun getRequiredParams(): Map<String, String> = mapOf("edits" to "object")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "edits" to "JSON object mapping file paths to their new content: {\"path/to/file.kt\": \"new content...\"}"
    )
    override fun getTimeoutMs(): Long = 120_000L
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val editsObj = args.getAsJsonObject("edits") ?: throw ToolError.MissingParam("edits")
        val edits = mutableMapOf<String, String>()
        editsObj.entrySet().forEach { entry ->
            val path = entry.key
            val content = entry.value.asString
            val file = resolvePathOrThrow(context, path)
            edits[file.absolutePath] = content
        }
        context.ideService.applyBatchEdits(edits)
        return McpToolResult.success("Batch edits for ${edits.size} files opened in Xed Editor for review.")
    }
}
