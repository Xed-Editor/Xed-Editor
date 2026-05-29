package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.File

class EditFileTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "editFile"
    override fun getDescription(): String =
        "Surgically edits a file by finding and replacing exact text. " +
            "Use this instead of writeFile when you only need to change specific parts of a file."

    override fun getRequiredParams(): Map<String, String> = mapOf(
        "filePath" to "string", "oldString" to "string", "newString" to "string"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file to edit",
        "oldString" to "The exact text to find in the file (must match exactly, including whitespace)",
        "newString" to "The replacement text"
    )
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "file" to "string",
        "dryRun" to "boolean", "partialMatch" to "boolean"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Alternative to filePath",
        "file" to "Alternative to filePath",
        "dryRun" to "If true, reports whether the edit would succeed without applying it (default: false)",
        "partialMatch" to "If true, allows matching a unique suffix/prefix of oldString when exact match fails (default: false)"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val oldString = requireString(args, "oldString")
        val newString = requireString(args, "newString")
        val dryRun = optionalBoolean(args, "dryRun")
        val partialMatch = optionalBoolean(args, "partialMatch")

        val file = resolvePathOrThrow(context, filePath)
        if (!file.exists()) throw ToolError.FileNotFound(filePath)

        val ideService = context.ideService
        val content = ideService.getFileContent(file.absolutePath)
            ?: runCatching { file.readText() }.getOrDefault("")

        if (content.isEmpty()) throw ToolError.InvalidParam("oldString", "file is empty")

        val index = content.indexOf(oldString)

        if (index == -1) {
            val oldTrimmed = oldString.trim()
            if (oldTrimmed.isNotBlank()) {
                val trimmedIndex = content.indexOf(oldTrimmed)
                if (trimmedIndex != -1 && content.indexOf(oldTrimmed, trimmedIndex + 1) == -1) {
                    val newContent = content.substring(0, trimmedIndex) + newString + content.substring(trimmedIndex + oldTrimmed.length)
                    if (dryRun) return McpToolResult.success("[dry-run] Found unique match by trimming whitespace. Would edit ${file.name}")
                    return applyEdit(ideService, file, content, newContent, filePath)
                }
            }

            if (partialMatch) {
                val lines = content.split("\n")
                val oldTrimmed = oldString.trim()
                val lineMatches = mutableListOf<Pair<Int, String>>()
                lines.forEachIndexed { i, line ->
                    if (line.contains(oldTrimmed)) lineMatches.add(i + 1 to line)
                }
                if (lineMatches.isEmpty()) {
                    val suggestions = lines.filter { it.length > 20 }
                        .map { it.trim().take(120) }
                        .take(5)
                    throw ToolError.InvalidParam("oldString",
                        "text not found in file. Did you mean one of these?\n" +
                            suggestions.joinToString("\n"))
                }
                if (lineMatches.size == 1) {
                    val (lineNum, lineContent) = lineMatches[0]
                    val newContent = content.replace(lineContent, newString)
                    if (dryRun) return McpToolResult.success("[dry-run] Would edit ${file.name} at line $lineNum")
                    return applyEdit(ideService, file, content, newContent, filePath)
                }
                throw ToolError.InvalidParam("oldString",
                    "text not found exactly. Found ${lineMatches.size} partial line matches at lines: ${lineMatches.joinToString(", ") { it.first.toString() }}. Use a more specific match.")
            }

            val similar = findSimilar(content, oldString)
            throw ToolError.InvalidParam("oldString",
                "text not found in ${file.name}.${if (similar.isNotEmpty()) " Did you mean:\n$similar" else ""}")
        }

        val nextIndex = content.indexOf(oldString, index + oldString.length)
        if (nextIndex != -1) {
            val occurrences = mutableListOf<Int>()
            var searchFrom = 0
            while (true) {
                val idx = content.indexOf(oldString, searchFrom)
                if (idx == -1) break
                val lineNum = content.substring(0, idx).count { it == '\n' } + 1
                occurrences.add(lineNum)
                searchFrom = idx + 1
            }
            throw ToolError.InvalidParam("oldString",
                "found ${occurrences.size} occurrences of oldString at lines: ${occurrences.joinToString(", ")}. " +
                    "Include more context from the surrounding code to make a unique match.")
        }

        val newContent = content.replaceRange(index, index + oldString.length, newString)
        if (dryRun) return McpToolResult.success("[dry-run] Would edit ${file.name} at character offset $index")

        return applyEdit(ideService, file, content, newContent, filePath)
    }

    private suspend fun applyEdit(
        ideService: com.rk.ai.service.IdeService, file: File, oldContent: String, newContent: String, filePath: String
    ): McpToolResult {
        showPatchAndApply(ideService, file, newContent, "Review AI surgical edit")
        return McpToolResult.success("Edit opened in Xed Editor for ${file.absolutePath}. Results will be sent via notifications.")
    }

    private fun findSimilar(content: String, query: String, maxSuggestions: Int = 3): String {
        val lines = content.split("\n")
        val words = query.split(Regex("\\s+")).filter { it.length > 3 }
        if (words.isEmpty()) return ""

        val scored = lines.mapIndexed { i, line ->
            val matchCount = words.count { word -> line.contains(word, ignoreCase = true) }
            Pair(i + 1, matchCount)
        }.filter { it.second > 0 }.sortedByDescending { it.second }

        return scored.take(maxSuggestions).joinToString("\n") { (line, score) ->
            val excerpt = lines.getOrNull(line - 1)?.trim()?.take(120) ?: ""
            "  line $line: $excerpt"
        }
    }
}
