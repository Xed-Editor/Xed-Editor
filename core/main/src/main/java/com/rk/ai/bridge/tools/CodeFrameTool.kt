package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService
import java.io.File

class GetCodeFrameTool : BaseMcpTool() {
    override fun getName(): String = "getCodeFrame"
    override fun getDescription(): String =
        "Returns lines around a specific location in a file, automatically expanding to show " +
        "the enclosing function or class scope. CRITICAL: Use this instead of readFile when you " +
        "need to understand the context around a symbol, error location, or search result. " +
        "It shows more relevant code than a fixed line range."

    override fun getRequiredParams(): Map<String, String> = mapOf(
        "filePath" to "string", "line" to "number"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file",
        "line" to "Line number (1-indexed) to center the frame around"
    )
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "column" to "number", "contextLines" to "number"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "column" to "Column number (1-indexed) for precise cursor location",
        "contextLines" to "Number of context lines above and below (default: 10, max: 50)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val centerLine = requireInt(args, "line")
        val contextLines = (optionalPositiveInt(args, "contextLines") ?: 10).coerceAtMost(50)
        val file = resolvePathOrThrow(ideService, filePath)

        if (!file.exists()) throw ToolError.FileNotFound(filePath)

        val content = ideService.getFileContent(file.absolutePath)
            ?: runCatching { file.readText() }.getOrDefault("")

        if (content.isEmpty()) return textResult("(empty file)")

        val lines = content.split("\n")
        val targetIndex = (centerLine - 1).coerceIn(0, lines.size - 1)

        var scopeStart = (targetIndex - contextLines).coerceAtLeast(0)
        var scopeEnd = (targetIndex + contextLines).coerceAtMost(lines.size - 1)

        for (i in targetIndex downTo 0) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("fun ") || trimmed.startsWith("class ") ||
                trimmed.startsWith("interface ") || trimmed.startsWith("object ") ||
                trimmed.startsWith("def ") || trimmed.startsWith("function ") ||
                trimmed.startsWith("struct ") || trimmed.startsWith("enum ") ||
                trimmed.startsWith("private ") || trimmed.startsWith("public ") ||
                trimmed.startsWith("internal ") || trimmed.startsWith("protected ") ||
                trimmed.endsWith("{") || trimmed.endsWith("):") ||
                trimmed.endsWith(") =") || trimmed.endsWith(") {")) {
                scopeStart = (i - 2).coerceAtLeast(0)
                break
            }
        }

        for (i in targetIndex until lines.size) {
            if (lines[i] == "}" || lines[i].trim() == "}") {
                scopeEnd = (i + 1).coerceAtMost(lines.size - 1)
                break
            }
        }

        val sb = StringBuilder()
        for (i in scopeStart..scopeEnd) {
            val marker = if (i == targetIndex) ">" else " "
            sb.appendLine("$marker ${i + 1}: ${lines[i]}")
        }

        val enclosing = lines.subList(scopeStart, targetIndex).lastOrNull { line ->
            val t = line.trim()
            t.startsWith("fun ") || t.startsWith("class ") || t.startsWith("interface ") ||
            t.startsWith("object ") || t.startsWith("def ") || t.startsWith("function ")
        }?.trim()?.take(120) ?: ""

        return textResult(buildString {
            appendLine("File: ${file.absolutePath}")
            appendLine("Target: line $centerLine")
            if (enclosing.isNotBlank()) appendLine("Scope: $enclosing")
            appendLine("Lines ${scopeStart + 1}-${scopeEnd + 1} (${scopeEnd - scopeStart + 1} lines):")
            appendLine("```")
            append(sb.toString().trimEnd())
            appendLine("\n```")
        })
    }
}
