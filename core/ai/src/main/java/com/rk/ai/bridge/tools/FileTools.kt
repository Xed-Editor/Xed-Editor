package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.File

private const val READ_TRUNCATION_LIMIT = 250_000
private const val MULTI_READ_TRUNCATION_LIMIT = 500_000

private abstract class FileReaderTool : BaseMcpTool() {
    suspend fun executeReadFile(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(context, filePath)
        val startLine = optionalInt(args, "startLine")
        val endLine = optionalInt(args, "endLine")
        val count = optionalInt(args, "lines") ?: optionalInt(args, "count")

        val content = if (startLine != null || endLine != null || count != null) {
            val s = (startLine ?: 1).coerceAtLeast(1)
            val e = endLine ?: count?.let { s + it.coerceAtLeast(1) - 1 }
            readLineRange(file, s, e)
        } else {
            val text = context.ideService.getFileContent(file.absolutePath, null, null).orEmpty()
            if (text.length > READ_TRUNCATION_LIMIT) {
                text.take(READ_TRUNCATION_LIMIT) + "\n\n... (truncated at 250KB. Use startLine/endLine to read specific sections)"
            } else {
                text
            }
        }
        return McpToolResult.success(content)
    }

    fun getReadFileParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string",
        "startLine" to "number", "endLine" to "number",
        "lines" to "number", "count" to "number"
    )

    fun getReadFileParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Absolute or relative path to the file",
        "filePath" to "Alternative to path",
        "file" to "Alternative to path",
        "startLine" to "First line to read (1-indexed)",
        "endLine" to "Last line to read (inclusive)",
        "lines" to "Number of lines to read from start",
        "count" to "Alias for lines"
    )
}

class ReadFileTool : FileReaderTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "readFile"
    override fun getDescription(): String = "NATIVE file reader. Much faster than terminal cat. Supports line range (startLine/endLine) and first-N-lines (lines/count). Accepts: path, filePath, file."
    override fun getOptionalParams(): Map<String, String> = getReadFileParams()
    override fun getOptionalParamDescriptions(): Map<String, String> = getReadFileParamDescriptions()
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = executeReadFile(args, context)
}

class CatTool : FileReaderTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "cat"
    override fun getDescription(): String = "Same as readFile but named 'cat' for agent convenience. Accepts: path, filePath, file."
    override fun getOptionalParams(): Map<String, String> = getReadFileParams()
    override fun getOptionalParamDescriptions(): Map<String, String> = getReadFileParamDescriptions()
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = executeReadFile(args, context)
}

class ReadFilesTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "readFiles"
    override fun getDescription(): String = "RECOMMENDED: Reads multiple files at once. Use this to gather context across several related files in a single turn. Input 'filePaths' can be a comma-separated string or a JSON array of strings."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePaths" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePaths" to "Comma-separated list of paths or JSON array of path strings"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val input = requireString(args, "filePaths")
        val paths = if (input.startsWith("[")) {
            runCatching { com.google.gson.JsonParser.parseString(input).asJsonArray.map { it.asString } }.getOrDefault(listOf(input))
        } else {
            input.split(",").map { it.trim() }
        }
        val output = StringBuilder()
        val ideService = context.ideService
        paths.forEach { path ->
            runCatching {
                val file = resolvePathOrThrow(context, path)
                val content = ideService.getFileContent(file.absolutePath, null, null).orEmpty()
                output.append("--- FILE: ").append(path).append(" ---\n")
                val limited = if (content.length > MULTI_READ_TRUNCATION_LIMIT) content.take(MULTI_READ_TRUNCATION_LIMIT) + "\n... (truncated at 500KB)" else content
                output.append(limited).append("\n\n")
            }.onFailure {
                output.append("--- FILE: ").append(path).append(" (ERROR: ").append(it.message).append(") ---\n\n")
            }
        }
        return McpToolResult.success(output.toString())
    }
}

class WriteFileTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "writeFile"
    override fun getDescription(): String = "Writes new content to a file. Use this for single-file updates. For cross-file changes, prefer 'applyBatchEdits'. Opens a review tab for the user."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "content" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file to write",
        "content" to "The full new content of the file"
    )
    override fun getBlankRequiredParams(): Set<String> = setOf("content")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val content = requireString(args, "content", allowBlank = true)
        val file = resolvePathOrThrow(context, filePath)
        val msg = showPatchAndApply(context.ideService, file, content, "Review Gemini file update", refreshAfterApply = false)
        return McpToolResult.success(msg)
    }
}

private abstract class DirectoryListerTool : BaseMcpTool() {
    suspend fun executeListFiles(args: JsonObject, context: McpToolContext): McpToolResult {
        val dirPath = getPathParam(args) ?: throw ToolError.MissingParam("path/directoryPath")
        val dir = resolvePathOrThrow(context, dirPath)
        val recursive = optionalBoolean(args, "recursive")
        val maxFiles = (optionalPositiveInt(args, "maxFiles") ?: 500).coerceIn(1, 5000)
        val files = context.ideService.listFiles(dir, recursive, maxFiles)
        return McpToolResult.success(files.joinToString("\n"))
    }

    fun getListFilesParams(): Map<String, String> = mapOf(
        "path" to "string", "directoryPath" to "string", "recursive" to "boolean", "maxFiles" to "number"
    )

    fun getListFilesParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Directory path to list",
        "directoryPath" to "Alternative to path",
        "recursive" to "List files recursively (default: false)",
        "maxFiles" to "Maximum number of files to return (default: 500, max: 5000)"
    )
}

class ListFilesTool : DirectoryListerTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "listFiles"
    override fun getDescription(): String = "NATIVE directory listing. Same as 'ls' but runs natively. Accepts: path, directoryPath."
    override fun getOptionalParams(): Map<String, String> = getListFilesParams()
    override fun getOptionalParamDescriptions(): Map<String, String> = getListFilesParamDescriptions()
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = executeListFiles(args, context)
}

class LsTool : DirectoryListerTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "ls"
    override fun getDescription(): String = "Same as listFiles. Lists directory contents. Accepts: path, directoryPath."
    override fun getOptionalParams(): Map<String, String> = getListFilesParams()
    override fun getOptionalParamDescriptions(): Map<String, String> = getListFilesParamDescriptions()
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = executeListFiles(args, context)
}

class OpenFileTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "openFile"
    override fun getDescription(): String = "Opens a file in an editor tab. Use this to focus the user's attention on a specific file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file to open"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(context, filePath)
        context.ideService.openFile(file)
        return McpToolResult.success("opened ${file.absolutePath}")
    }
}

class CreateFileTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "createFile"
    override fun getDescription(): String = "Creates a new file on disk. Use this when starting new modules or adding assets."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("content" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path for the new file"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "content" to "Initial file content (optional)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val content = optionalString(args, "content")
        val result = context.ideService.createFile(filePath, content.ifBlank { null })
        return McpToolResult.success(result)
    }
}

class DeleteFileTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "deleteFile"
    override fun getDescription(): String = "Deletes a file from the workspace. Use with caution."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path of the file to delete"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val result = context.ideService.deleteFile(filePath)
        return McpToolResult.success(result)
    }
}

private abstract class FileMoveTool : BaseMcpTool() {
    suspend fun executeMoveFile(args: JsonObject, context: McpToolContext): McpToolResult {
        val sourcePath = requireString(args, "sourcePath")
        val destPath = requireString(args, "destPath")
        val result = context.ideService.renameFile(sourcePath, destPath)
        return McpToolResult.success(result)
    }

    fun getMoveFileParams(): Map<String, String> = mapOf("sourcePath" to "string", "destPath" to "string")
    fun getMoveFileParamDescriptions(): Map<String, String> = mapOf(
        "sourcePath" to "Current path of the file or directory",
        "destPath" to "New path for the file or directory"
    )
}

class RenameFileTool : FileMoveTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "renameFile"
    override fun getDescription(): String = "Moves or renames a file or directory. Updates disk state immediately."
    override fun getRequiredParams(): Map<String, String> = getMoveFileParams()
    override fun getRequiredParamDescriptions(): Map<String, String> = getMoveFileParamDescriptions()
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = executeMoveFile(args, context)
}

class MoveFileTool : FileMoveTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "moveFile"
    override fun getDescription(): String = "Alias for renameFile. Moves a file or directory to a new workspace path."
    override fun getRequiredParams(): Map<String, String> = getMoveFileParams()
    override fun getRequiredParamDescriptions(): Map<String, String> = getMoveFileParamDescriptions()
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = executeMoveFile(args, context)
}

private abstract class DirectoryCreatorTool : BaseMcpTool() {
    suspend fun executeCreateDirectory(args: JsonObject, context: McpToolContext): McpToolResult {
        val path = getPathParam(args) ?: throw ToolError.MissingParam("directoryPath/path")
        val parents = optionalBoolean(args, "parents", true)
        val dir = resolvePathOrThrow(context, path)
        if (dir.exists() && !dir.isDirectory) throw ToolError.InvalidParam("directoryPath", "path exists and is not a directory: ${dir.absolutePath}")
        val created = if (parents) dir.mkdirs() else dir.mkdir()
        if (!created && !dir.isDirectory) throw ToolError.InvalidParam("directoryPath", "failed to create directory: ${dir.absolutePath}")
        return McpToolResult.success("created directory ${dir.absolutePath}")
    }

    fun getCreateDirectoryParams(): Pair<Map<String, String>, Map<String, String>> = mapOf(
        "directoryPath" to "string", "parents" to "boolean"
    ) to mapOf(
        "directoryPath" to "Workspace directory path to create",
        "parents" to "Create parent directories as needed (default: true)"
    )
}

class CreateDirectoryTool : DirectoryCreatorTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "createDirectory"
    override fun getDescription(): String = "Creates a directory or nested directory structure inside the workspace."
    override fun getRequiredParams(): Map<String, String> = mapOf("directoryPath" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("parents" to "boolean")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "directoryPath" to "Workspace directory path to create"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "parents" to "Create parent directories as needed (default: true)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = executeCreateDirectory(args, context)
}

class MkdirTool : DirectoryCreatorTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "mkdir"
    override fun getDescription(): String = "Alias for createDirectory. Creates a directory or nested directory structure."
    override fun getRequiredParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("parents" to "boolean")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf("path" to "Workspace directory path to create")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf("parents" to "Create parent directories as needed (default: true)")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = executeCreateDirectory(args, context)
}
