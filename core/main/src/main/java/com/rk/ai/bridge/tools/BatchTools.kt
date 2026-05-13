package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class ApplyBatchEditsTool : BaseMcpTool() {
    override fun getName(): String = "applyBatchEdits"
    override fun getDescription(): String = "Applies multiple file changes at once. Takes a JSON object where keys are file paths and values are new content."
    override fun getRequiredParams(): Map<String, String> = mapOf("edits" to "object")
    
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val editsObj = args.getAsJsonObject("edits") ?: throw ToolError.MissingParam("edits")
        val edits = mutableMapOf<String, String>()
        editsObj.entrySet().forEach { entry ->
            val path = entry.key
            val content = entry.value.asString
            val file = resolvePathOrThrow(ideService, path)
            edits[file.absolutePath] = content
        }
        
        ideService.applyBatchEdits(edits)
        return textResult("Batch edits for ${edits.size} files opened in Xed Editor for review.")
    }
}
