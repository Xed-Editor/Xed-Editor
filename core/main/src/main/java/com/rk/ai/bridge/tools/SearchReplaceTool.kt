package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService
import java.io.File

class SearchAndReplaceTool : BaseMcpTool() {
    override fun getName(): String = "searchAndReplace"
    override fun getDescription(): String =
        "Finds files matching a glob pattern and performs find-and-replace across all of them. " +
        "CRITICAL: Use this for batch refactoring (renaming variables, updating imports, " +
        "changing API calls) across multiple files. For single-file edits, prefer editFile."

    override fun getRequiredParams(): Map<String, String> = mapOf(
        "pattern" to "string", "oldString" to "string", "newString" to "string"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "pattern" to "Glob pattern to find files (e.g. '*.kt', 'src/**/*.kt'). CRITICAL: Use ** for recursive search.",
        "oldString" to "The exact text to find and replace",
        "newString" to "The replacement text"
    )
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "dryRun" to "boolean", "isRegex" to "boolean"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Directory to search in (default: workspace root)",
        "dryRun" to "If true, reports which files would change without modifying them",
        "isRegex" to "If true, treat oldString as a regex pattern"
    )

    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val pattern = requireString(args, "pattern")
        val oldString = requireString(args, "oldString")
        val newString = requireString(args, "newString")
        val path = getPathParam(args)
        val dryRun = optionalBoolean(args, "dryRun")
        val isRegex = optionalBoolean(args, "isRegex")

        val root = if (path != null) {
            ideService.resolvePath(path) ?: File(ideService.getPrimaryWorkspacePath())
        } else {
            File(ideService.getPrimaryWorkspacePath())
        }
        if (!root.exists() || !root.isDirectory) throw ToolError.PathOutsideWorkspace("$path is not a valid directory")

        val files = mutableListOf<File>()
        root.walkTopDown()
            .maxDepth(Int.MAX_VALUE)
            .filter { it.isFile && globMatches(it.name, pattern) }
            .forEach { files.add(it) }

        if (files.isEmpty()) return textResult("No files found matching pattern: $pattern")

        val result = buildString {
            appendLine("Found ${files.size} file(s) matching '$pattern':")
            var changedCount = 0
            files.forEach { file ->
                val content = runCatching { file.readText() }.getOrNull() ?: return@forEach
                val oldSearch = if (isRegex) oldString else Regex.escape(oldString)
                val regex = runCatching { Regex(oldSearch) }.getOrNull() ?: return@forEach
                val matchCount = regex.findAll(content).count()
                if (matchCount > 0) {
                    if (!dryRun) {
                        val newContent = content.replace(regex, newString)
                        file.writeText(newContent)
                    }
                    appendLine("  ${file.absolutePath}: $matchCount occurrence(s)")
                    changedCount++
                }
            }
            if (dryRun) appendLine("\n[dry-run] Would modify $changedCount file(s)")
            else appendLine("\nModified $changedCount file(s)")
        }
        return textResult(result)
    }

    private fun globMatches(name: String, glob: String): Boolean {
        val regexStr = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        regexStr.append(".*"); i++
                        if (i + 1 < glob.length && glob[i + 1] == '/') i++
                    } else regexStr.append("[^/]*")
                }
                '?' -> regexStr.append(".")
                '.' -> regexStr.append("\\.")
                else -> regexStr.append(Regex.escape(c.toString()))
            }
            i++
        }
        regexStr.append("$")
        return Regex(regexStr.toString(), RegexOption.IGNORE_CASE).matches(name)
    }
}
