package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService
import java.io.File

class ReadFileTool : BaseMcpTool() {
    override fun getName(): String = "readFile"
    override fun getDescription(): String = "NATIVE file reader - DO NOT use runCommand('cat ...'). Much faster than terminal cat. Supports line range (startLine/endLine) and first-N-lines (lines/count). Accepts: path, filePath, file."
    override fun getRequiredParams(): Map<String, String> = emptyMap()
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string",
        "startLine" to "number", "endLine" to "number",
        "lines" to "number", "count" to "number"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Absolute or relative path to the file",
        "filePath" to "Alternative to path",
        "file" to "Alternative to path",
        "startLine" to "First line to read (1-indexed)",
        "endLine" to "Last line to read (inclusive)",
        "lines" to "Number of lines to read from start",
        "count" to "Alias for lines"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(ideService, filePath)
        val startLine = optionalInt(args, "startLine")
        val endLine = optionalInt(args, "endLine")
        val count = optionalInt(args, "lines") ?: optionalInt(args, "count")

        val content = if (startLine != null || endLine != null || count != null) {
            val s = if (startLine != null) startLine else 1
            val e = if (endLine != null) endLine else if (count != null) s + count - 1 else null
            readLineRange(file, s, e)
        } else {
            ideService.getFileContent(file.absolutePath, null, null).orEmpty()
        }
        return textResult(content)
    }
}

class CatTool : BaseMcpTool() {
    override fun getName(): String = "cat"
    override fun getDescription(): String = "NATIVE cat replacement - DO NOT use runCommand('cat ...'). Same as readFile but named 'cat' for agent convenience. Accepts: path, filePath, file."
    override fun getRequiredParams(): Map<String, String> = emptyMap()
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string",
        "startLine" to "number", "endLine" to "number",
        "lines" to "number", "count" to "number"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Absolute or relative path to the file",
        "filePath" to "Alternative to path",
        "file" to "Alternative to path",
        "startLine" to "First line to read (1-indexed)",
        "endLine" to "Last line to read (inclusive)",
        "lines" to "Number of lines to read from start",
        "count" to "Alias for lines"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(ideService, filePath)
        val startLine = optionalInt(args, "startLine")
        val endLine = optionalInt(args, "endLine")
        val count = optionalInt(args, "lines") ?: optionalInt(args, "count")

        val content = if (startLine != null || endLine != null || count != null) {
            val s = if (startLine != null) startLine else 1
            val e = if (endLine != null) endLine else if (count != null) s + count - 1 else null
            readLineRange(file, s, e)
        } else {
            ideService.getFileContent(file.absolutePath, null, null).orEmpty()
        }
        return textResult(content)
    }
}

class ReadFilesTool : BaseMcpTool() {
    override fun getName(): String = "readFiles"
    override fun getDescription(): String = "RECOMMENDED: Reads multiple files at once. Use this to gather context across several related files in a single turn. Input 'filePaths' can be a comma-separated string or a JSON array of strings."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePaths" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePaths" to "Comma-separated list of paths or JSON array of path strings"
    )
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "startLine" to "number", "endLine" to "number", "lines" to "number", "count" to "number"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "startLine" to "First line to read per file (1-indexed)",
        "endLine" to "Last line to read per file (inclusive)",
        "lines" to "Number of lines to read per file",
        "count" to "Alias for lines"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val input = requireString(args, "filePaths")
        val paths = if (input.startsWith("[")) {
            runCatching { com.google.gson.JsonParser.parseString(input).asJsonArray.map { it.asString } }.getOrDefault(listOf(input))
        } else {
            input.split(",").map { it.trim() }
        }
        val startLine = optionalInt(args, "startLine")
        val endLine = optionalInt(args, "endLine")
        val count = optionalInt(args, "lines") ?: optionalInt(args, "count")
        val output = StringBuilder()
        paths.forEach { path ->
            runCatching {
                val file = resolvePathOrThrow(ideService, path)
                val content = if (startLine != null || endLine != null || count != null) {
                    readLineRange(file, startLine ?: 1, endLine ?: if (count != null) startLine!! + count - 1 else null)
                } else {
                    ideService.getFileContent(file.absolutePath, null, null).orEmpty()
                }
                output.append("--- FILE: ").append(path).append(" ---\n")
                val limited = if (content.length > 500_000) content.take(500_000) + "\n... (truncated at 500KB)" else content
                output.append(limited).append("\n\n")
            }.onFailure {
                output.append("--- FILE: ").append(path).append(" (ERROR: ").append(it.message).append(") ---\n\n")
            }
        }
        return textResult(output.toString())
    }
}

class WriteFileTool : BaseMcpTool() {
    override fun getName(): String = "writeFile"
    override fun getDescription(): String = "Writes new content to a file. Use this for single-file updates. For cross-file changes, prefer 'applyBatchEdits'. Opens a review tab for the user."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "content" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file to write",
        "content" to "The full new content of the file"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val content = requireString(args, "content")
        val file = resolvePathOrThrow(ideService, filePath)
        file.parentFile?.mkdirs()
        val msg = showPatchAndApply(ideService, file, content, "Review AI file update", refreshAfterApply = false)
        return textResult(msg)
    }
}

class ListFilesTool : BaseMcpTool() {
    override fun getName(): String = "listFiles"
    override fun getDescription(): String = "NATIVE directory listing - DO NOT use runCommand('ls ...'). Same as 'ls' but runs natively without shell overhead. Accepts: path, directoryPath."
    override fun getRequiredParams(): Map<String, String> = emptyMap()
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string", "directoryPath" to "string", "recursive" to "boolean", "maxFiles" to "number")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Directory path to list",
        "directoryPath" to "Alternative to path",
        "recursive" to "List files recursively (default: false)",
        "maxFiles" to "Maximum number of files to return (default: 500, max: 5000)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val dirPath = getPathParam(args) ?: throw ToolError.MissingParam("path/directoryPath")
        val dir = resolvePathOrThrow(ideService, dirPath)
        val recursive = optionalBoolean(args, "recursive")
        val maxFiles = (optionalPositiveInt(args, "maxFiles") ?: 500).coerceIn(1, 5000)
        val files = ideService.listFiles(dir, recursive, maxFiles)
        return textResult(files.joinToString("\n"))
    }
}

class LsTool : BaseMcpTool() {
    override fun getName(): String = "ls"
    override fun getDescription(): String = "NATIVE ls replacement - DO NOT use runCommand('ls ...'). Same as listFiles. Lists directory contents with no shell overhead. Accepts: path, directoryPath."
    override fun getRequiredParams(): Map<String, String> = emptyMap()
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string", "directoryPath" to "string", "recursive" to "boolean", "maxFiles" to "number")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Directory path to list",
        "directoryPath" to "Alternative to path",
        "recursive" to "List files recursively (default: false)",
        "maxFiles" to "Maximum number of files to return (default: 500, max: 5000)"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val dirPath = getPathParam(args) ?: throw ToolError.MissingParam("path/directoryPath")
        val dir = resolvePathOrThrow(ideService, dirPath)
        val recursive = optionalBoolean(args, "recursive")
        val maxFiles = (optionalPositiveInt(args, "maxFiles") ?: 500).coerceIn(1, 5000)
        val files = ideService.listFiles(dir, recursive, maxFiles)
        return textResult(files.joinToString("\n"))
    }
}

class OpenFileTool : BaseMcpTool() {
    override fun getName(): String = "openFile"
    override fun getDescription(): String = "Opens a file in an editor tab. Use this to focus the user's attention on a specific file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path to the file to open"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(ideService, filePath)
        file.parentFile?.mkdirs()
        ideService.openFile(file)
        return textResult("opened ${file.absolutePath}")
    }
}

class CreateFileTool : BaseMcpTool() {
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
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val content = optionalString(args, "content")
        val result = ideService.createFile(filePath, content.ifBlank { null })
        return textResult(result)
    }
}

class DeleteFileTool : BaseMcpTool() {
    override fun getName(): String = "deleteFile"
    override fun getDescription(): String = "Deletes a file from the workspace. Use with caution."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "filePath" to "Absolute path of the file to delete"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val result = ideService.deleteFile(filePath)
        return textResult(result)
    }
}

class RenameFileTool : BaseMcpTool() {
    override fun getName(): String = "renameFile"
    override fun getDescription(): String = "Moves or renames a file. Updates disk state immediately."
    override fun getRequiredParams(): Map<String, String> = mapOf("sourcePath" to "string", "destPath" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "sourcePath" to "Current path of the file",
        "destPath" to "New path for the file"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val sourcePath = requireString(args, "sourcePath")
        val destPath = requireString(args, "destPath")
        val result = ideService.renameFile(sourcePath, destPath)
        return textResult(result)
    }
}