package com.rk.ai.coding.tools

class ReadFileTool : NativeTool {
    override val name: String = "readFile"
    override val description: String = "Read a workspace file, optionally limited to a line range."
    override val permission: ToolPermissionLevel = ToolPermissionLevel.AutoAllow

    override fun inputSchema() = objectSchema(
        properties = mapOf(
            "path" to "string",
            "startLine" to "number",
            "endLine" to "number",
            "lines" to "number",
        ),
        required = listOf("path"),
    )

    override suspend fun execute(args: com.google.gson.JsonObject, context: NativeToolContext): NativeToolResult {
        val path = args.stringParam("path", "filePath", "file")
        if (path.isBlank()) throw NativeToolError("path required")
        val file = context.fileOps.resolvePath(path) ?: throw NativeToolError("path outside workspace: $path")
        val startLine = args.intParam("startLine")
        val count = args.intParam("lines") ?: args.intParam("count")
        val endLine = args.intParam("endLine") ?: count?.let { (startLine ?: 1) + it.coerceAtLeast(1) - 1 }
        val text = context.fileOps.getFileContent(file.absolutePath, startLine, endLine).orEmpty()
        return NativeToolResult.success(text.truncate(250_000, "Use startLine/endLine to read specific sections."))
    }
}

class WriteFileTool : NativeTool {
    override val name: String = "writeFile"
    override val description: String = "Open a reviewed patch for a workspace file. The file is not modified until the preview is approved."
    override val permission: ToolPermissionLevel = ToolPermissionLevel.Ask

    override fun inputSchema() = objectSchema(
        properties = mapOf("filePath" to "string", "content" to "string"),
        required = listOf("filePath", "content"),
    )

    override suspend fun execute(args: com.google.gson.JsonObject, context: NativeToolContext): NativeToolResult {
        val path = args.stringParam("filePath", "path", "file")
        if (path.isBlank()) throw NativeToolError("filePath required")
        val content = args.optionalStringParam("content", "newContent")
        val file = context.fileOps.resolvePath(path) ?: throw NativeToolError("path outside workspace: $path")
        val oldContent = context.fileOps.getFileContent(file.absolutePath).orEmpty()
        context.editorOps.showPatch(file.absolutePath, oldContent, content, "Review native coding agent change") {
            context.fileOps.writeFile(file, content)
            context.fileOps.refreshEditors(file.absolutePath, force = false)
        }
        return NativeToolResult.success("Review opened in Xed Editor for ${file.absolutePath}")
    }
}

class SearchTool : NativeTool {
    override val name: String = "searchWorkspace"
    override val description: String = "Search text in the workspace using the existing project search service."
    override val permission: ToolPermissionLevel = ToolPermissionLevel.AutoAllow

    override fun inputSchema() = objectSchema(
        properties = mapOf("query" to "string", "limit" to "number", "path" to "string", "regex" to "boolean"),
        required = listOf("query"),
    )

    override suspend fun execute(args: com.google.gson.JsonObject, context: NativeToolContext): NativeToolResult {
        val query = args.stringParam("query", "pattern", "search", "text")
        if (query.isBlank()) throw NativeToolError("query required")
        val limit = (args.intParam("limit") ?: 50).coerceIn(1, 500)
        val path = args.optionalStringParam("path").ifBlank { null }
        val regex = args.booleanParam("regex", false)
        val results = context.projectOps.searchCode(query, limit, path, regex)
        val text = buildString {
            results.forEach { element ->
                val item = element.asJsonObject
                append(item.get("path")?.asString.orEmpty())
                append(':')
                append(item.get("line")?.asInt ?: 0)
                append(": ")
                appendLine(item.get("snippet")?.asString.orEmpty().trim())
            }
        }.trim()
        return NativeToolResult.success(text.ifBlank { "No results found." })
    }
}

class ListFilesTool : NativeTool {
    override val name: String = "listFiles"
    override val description: String = "List workspace files without invoking a shell."
    override val permission: ToolPermissionLevel = ToolPermissionLevel.AutoAllow

    override fun inputSchema() = objectSchema(
        properties = mapOf("path" to "string", "recursive" to "boolean", "maxFiles" to "number"),
        required = listOf("path"),
    )

    override suspend fun execute(args: com.google.gson.JsonObject, context: NativeToolContext): NativeToolResult {
        val path = args.stringParam("path", "directoryPath")
        if (path.isBlank()) throw NativeToolError("path required")
        val directory = context.fileOps.resolvePath(path) ?: throw NativeToolError("path outside workspace: $path")
        val recursive = args.booleanParam("recursive", false)
        val maxFiles = (args.intParam("maxFiles") ?: 500).coerceIn(1, 5000)
        return NativeToolResult.success(context.fileOps.listFiles(directory, recursive, maxFiles).joinToString("\n"))
    }
}

class GitStatusTool : NativeTool {
    override val name: String = "gitStatus"
    override val description: String = "Read repository status for the current workspace."
    override val permission: ToolPermissionLevel = ToolPermissionLevel.AutoAllow

    override fun inputSchema() = objectSchema(properties = mapOf("path" to "string"))

    override suspend fun execute(args: com.google.gson.JsonObject, context: NativeToolContext): NativeToolResult {
        val path = args.optionalStringParam("path").ifBlank { context.projectOps.getPrimaryWorkspacePath() }
        val status = context.gitOps.getGitStatus(path)
        return if (status.has("error")) NativeToolResult.error(status.toString()) else NativeToolResult.success(status.toString())
    }
}

class GitDiffTool : NativeTool {
    override val name: String = "gitDiff"
    override val description: String = "Read repository diff for the current workspace."
    override val permission: ToolPermissionLevel = ToolPermissionLevel.AutoAllow

    override fun inputSchema() = objectSchema(properties = mapOf("path" to "string"))

    override suspend fun execute(args: com.google.gson.JsonObject, context: NativeToolContext): NativeToolResult {
        val path = args.optionalStringParam("path").ifBlank { context.projectOps.getPrimaryWorkspacePath() }
        val diff = context.gitOps.getGitDiff(path)
        return if (diff.trim().startsWith("error:", ignoreCase = true)) NativeToolResult.error(diff) else NativeToolResult.success(diff)
    }
}

class TerminalReadTool : NativeTool {
    override val name: String = "terminalRead"
    override val description: String = "Read recent terminal output. This tool never executes commands."
    override val permission: ToolPermissionLevel = ToolPermissionLevel.AutoAllow

    override fun inputSchema() = objectSchema(properties = mapOf("lines" to "number"))

    override suspend fun execute(args: com.google.gson.JsonObject, context: NativeToolContext): NativeToolResult {
        val lines = args.intParam("lines")?.coerceAtLeast(1)
        return NativeToolResult.success(context.terminalOps.getTerminalOutput(lines))
    }
}

private fun String.truncate(maxChars: Int, hint: String): String =
    if (length <= maxChars) this else take(maxChars) + "\n\n... (truncated at $maxChars chars. $hint)"
