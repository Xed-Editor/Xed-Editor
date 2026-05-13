package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService
import java.io.File

class EditFileTool : BaseMcpTool() {
    override fun getName(): String = "editFile"
    override fun getDescription(): String =
        "Surgically edits a file by finding and replacing exact text. " +
            "Use this instead of writeFile when you only need to change specific parts of a file. " +
            "The tool reads the current file content, finds oldString, replaces it with newString, " +
            "and opens a diff review for the user. On mismatch, it automatically tries whitespace-flexible " +
            "matching. If oldString appears multiple times, the operation fails with a list of occurrences " +
            "so you can provide more context for a unique match."

    override fun getRequiredParams(): Map<String, String> = mapOf(
        "filePath" to "string", "oldString" to "string", "newString" to "string"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file to edit",
        "oldString" to "The exact text to find in the file (whitespace around lines is flexible)",
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
        "partialMatch" to "If true, allows matching a unique substring of oldString when exact and flexible matches fail (default: false)"
    )

    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        var oldString = requireString(args, "oldString")
        val newString = requireString(args, "newString")
        val dryRun = optionalBoolean(args, "dryRun")
        val partialMatch = optionalBoolean(args, "partialMatch")

        val file = resolvePathOrThrow(ideService, filePath)
        if (!file.exists()) throw ToolError.FileNotFound(filePath)

        val content = ideService.getFileContent(file.absolutePath)
            ?: runCatching { file.readText() }.getOrDefault("")

        if (content.isEmpty()) throw ToolError.InvalidParam("oldString", "file is empty")

        val result = findAndReplace(content, oldString, newString)

        if (result != null) {
            val (newContent, matchLine) = result
            if (dryRun) return textResult("[dry-run] Would edit ${file.name} at line $matchLine" +
                if (oldString != result.third) " (whitespace-flexible match)" else "")
            return applyEdit(ideService, file, content, newContent, filePath)
        }

        if (partialMatch) {
            val lines = content.split("\n")
            val oldTrimmed = oldString.trim()
            val lineMatches = mutableListOf<Pair<Int, String>>()
            lines.forEachIndexed { i, line ->
                if (line.contains(oldTrimmed)) lineMatches.add(i + 1 to line)
            }
            if (lineMatches.isEmpty()) {
                val suggestions = lines.filter { it.length > 20 }.take(5)
                throw ToolError.InvalidParam("oldString",
                    "text not found in file. Did you mean one of these?\n" +
                        suggestions.joinToString("\n"))
            }
            if (lineMatches.size == 1) {
                val (lineNum, lineContent) = lineMatches[0]
                val newContent = content.replace(lineContent, newString)
                if (dryRun) return textResult("[dry-run] Would edit ${file.name} at line $lineNum")
                return applyEdit(ideService, file, content, newContent, filePath)
            }
            throw ToolError.InvalidParam("oldString",
                "text not found exactly. Found ${lineMatches.size} partial line matches at lines: ${lineMatches.joinToString(", ") { it.first.toString() }}. Use a more specific match.")
        }

        val similar = findSimilar(content, oldString)
        throw ToolError.InvalidParam("oldString",
            "text not found in ${file.name}.${if (similar.isNotEmpty()) " Did you mean:\n$similar" else ""}")
    }

    /**
     * Tries matching in order:
     * 1. Exact match
     * 2. Trim leading/trailing whitespace from each line of oldString and match
     * 3. Match oldString trimmed as a whole
     * Returns (newContent, lineNumber, matchedString) or null.
     */
    private fun findAndReplace(
        content: String, oldString: String, newString: String
    ): Triple<String, Int, String>? {
        val exactIdx = content.indexOf(oldString)
        if (exactIdx != -1) {
            val nextIdx = content.indexOf(oldString, exactIdx + oldString.length)
            if (nextIdx != -1) {
                val occurrences = findAllOccurrences(content, oldString)
                throw ToolError.InvalidParam("oldString",
                    "found ${occurrences.size} occurrences at lines: ${occurrences.joinToString(", ")}. " +
                        "Include more surrounding context for a unique match.")
            }
            val lineNum = content.substring(0, exactIdx).count { it == '\n' } + 1
            val newContent = content.replaceRange(exactIdx, exactIdx + oldString.length, newString)
            return Triple(newContent, lineNum, oldString)
        }

        val trimmedOld = oldString.trim()
        if (trimmedOld != oldString) {
            val trimmedIdx = content.indexOf(trimmedOld)
            if (trimmedIdx != -1) {
                val lineNum = content.substring(0, trimmedIdx).count { it == '\n' } + 1
                val newContent = content.replaceRange(trimmedIdx, trimmedIdx + trimmedOld.length, newString)
                return Triple(newContent, lineNum, trimmedOld)
            }
        }

        val normalizedOld = normalize(oldString)
        if (normalizedOld != oldString && normalizedOld != trimmedOld) {
            val contentLines = content.split("\n")
            val oldLines = normalizedOld.split("\n")
            if (oldLines.size <= contentLines.size) {
                for (startLine in 0..contentLines.size - oldLines.size) {
                    val window = contentLines.subList(startLine, startLine + oldLines.size)
                    if (normalize(window.joinToString("\n")) == normalizedOld) {
                        val matched = window.joinToString("\n")
                        val lineNum = startLine + 1
                        val newContentLines = contentLines.toMutableList()
                        newContentLines.removeRange(startLine, startLine + oldLines.size)
                        newContentLines.addAll(startLine, newString.split("\n"))
                        val newContent = newContentLines.joinToString("\n")
                        return Triple(newContent, lineNum, matched)
                    }
                }
            }
        }

        return null
    }

    private fun normalize(text: String): String =
        text.lines().joinToString("\n") { it.trim() }

    private fun findAllOccurrences(content: String, search: String): List<Int> {
        val lines = mutableListOf<Int>()
        var from = 0
        while (true) {
            val idx = content.indexOf(search, from)
            if (idx == -1) break
            lines.add(content.substring(0, idx).count { it == '\n' } + 1)
            from = idx + 1
        }
        return lines
    }

    private suspend fun applyEdit(
        ideService: IdeService, file: File, oldContent: String, newContent: String, filePath: String
    ): JsonObject {
        ideService.showPatch(file.absolutePath, oldContent, newContent, "Review AI surgical edit") {
            ideService.writeFile(file, newContent)
        }
        return textResult("Edit opened in Xed Editor for ${file.absolutePath}. Results will be sent via notifications.")
    }

    private fun findSimilar(content: String, query: String, maxSuggestions: Int = 3): String {
        val lines = content.split("\n")
        val words = query.split(Regex("\\s+")).filter { it.length > 3 }
        if (words.isEmpty()) return ""

        val scored = lines.mapIndexed { i, line ->
            val matchCount = words.count { word -> line.contains(word, ignoreCase = true) }
            Pair(i + 1, matchCount)
        }.filter { it.second > 0 }.sortedByDescending { it.second }

        return scored.take(maxSuggestions).joinToString("\n") { (line, _) ->
            val excerpt = lines.getOrNull(line - 1)?.take(120) ?: ""
            "  line $line: $excerpt"
        }
    }
}
