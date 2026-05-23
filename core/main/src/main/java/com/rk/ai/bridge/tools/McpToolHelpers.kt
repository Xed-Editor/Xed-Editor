package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolResult
import com.rk.ai.service.IdeService
import java.io.File

fun resultText(text: String): McpToolResult = McpToolResult.text(text)

fun resultJson(data: JsonElement): McpToolResult = McpToolResult.json(
    if (data is JsonObject) data else JsonObject().apply { add("data", data) }
)

fun resultEmpty(): McpToolResult = McpToolResult.text("")

fun enforceOutputLimit(text: String): String = Security.enforceOutputLimit(text)

suspend fun showPatchAndApply(
    context: com.rk.ai.bridge.McpToolContext,
    file: File,
    newContent: String,
    title: String = "Review file change",
    refreshAfterApply: Boolean = true,
): String {
    val ideService = context.ideService
    val oldContent = ideService.getFileContent(file.absolutePath)
        ?: runCatching { file.readText() }.getOrDefault("")
    ideService.showPatch(file.absolutePath, oldContent, newContent, title) {
        ideService.writeFile(file, newContent)
        if (refreshAfterApply) ideService.refreshEditors(force = false)
    }
    return "Review opened in Xed Editor for ${file.absolutePath}. Results will be sent via notifications."
}
