package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService
import java.io.File

class ReadProjectFilesTool : BaseMcpTool() {
    override fun getName(): String = "readProjectFiles"
    override fun getDescription(): String =
        "Finds files matching a glob pattern and reads their content in one step. " +
        "CRITICAL: This combines findFiles + readFiles into a single call. " +
        "Use this when you need to understand multiple related files at once " +
        "(e.g., all files in a package, all test files, all config files)."

    override fun getRequiredParams(): Map<String, String> = mapOf("pattern" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "pattern" to "Glob pattern to find files. Examples: 'src/**/*.kt', '**/*.gradle.kts', 'app/src/**/*.xml'"
    )
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "limit" to "number",
        "startLine" to "number", "endLine" to "number", "lines" to "number"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Directory to search in (default: workspace root)",
        "limit" to "Maximum files to read (default: 10, max: 50)",
        "startLine" to "First line to read from each file (1-indexed)",
        "endLine" to "Last line to read from each file (inclusive)",
        "lines" to "Number of lines to read from start of each file"
    )

    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val pattern = requireString(args, "pattern")
        val path = getPathParam(args)
        val limit = (optionalPositiveInt(args, "limit") ?: 10).coerceIn(1, 50)
        val startLine = optionalInt(args, "startLine")
        val endLine = optionalInt(args, "endLine")
        val count = optionalInt(args, "lines")

        val root = if (path != null) {
            ideService.resolvePath(path) ?: File(ideService.getPrimaryWorkspacePath())
        } else {
            File(ideService.getPrimaryWorkspacePath())
        }

        val files = mutableListOf<File>()
        root.walkTopDown()
            .maxDepth(Int.MAX_VALUE)
            .filter { it.isFile && simpleGlob(it.name, pattern) }
            .take(limit)
            .forEach { files.add(it) }

        if (files.isEmpty()) return textResult("No files found matching: $pattern")

        val output = StringBuilder()
        files.forEach { file ->
            runCatching {
                val content = if (startLine != null || endLine != null || count != null) {
                    val s = startLine ?: 1
                    val e = endLine ?: if (count != null) s + count - 1 else null
                    readLineRange(file, s, e)
                } else {
                    file.readText()
                }
                val maxSize = 100_000
                output.appendLine("--- ${file.absolutePath} ---")
                output.appendLine(if (content.length > maxSize) content.take(maxSize) + "\n... (truncated at 100KB)" else content)
                output.appendLine()
            }.onFailure {
                output.appendLine("--- ${file.absolutePath} (ERROR: ${it.message}) ---")
                output.appendLine()
            }
        }
        return textResult(output.toString().trimEnd())
    }

    private fun simpleGlob(name: String, glob: String): Boolean {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        sb.append(".*"); i++
                        if (i + 1 < glob.length && glob[i + 1] == '/') i++
                    } else sb.append("[^/]*")
                }
                '?' -> sb.append(".")
                '.' -> sb.append("\\.")
                else -> sb.append(Regex.escape(c.toString()))
            }
            i++
        }
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE).matches(name)
    }
}
