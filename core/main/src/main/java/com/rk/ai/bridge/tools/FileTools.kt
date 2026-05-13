package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService
import java.io.File

class ReadFileTool : BaseMcpTool() {
    override fun getName(): String = "readFile"
    override fun getDescription(): String = "Reads the content of a file. Supports optional line range for large files."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("startLine" to "number", "endLine" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val startLine = optionalInt(args, "startLine")
        val endLine = optionalInt(args, "endLine")
        val file = resolvePathOrThrow(ideService, filePath)
        val content = ideService.getFileContent(file.absolutePath, startLine, endLine).orEmpty()
        return textResult(content)
    }
}

class ReadFilesTool : BaseMcpTool() {
    override fun getName(): String = "readFiles"
    override fun getDescription(): String = "RECOMMENDED: Reads multiple files at once. Use this to gather context across several related files in a single turn. Input 'filePaths' can be a comma-separated string or a JSON array of strings."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePaths" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val input = requireString(args, "filePaths")
        val paths = if (input.startsWith("[")) {
            runCatching { com.google.gson.JsonParser.parseString(input).asJsonArray.map { it.asString } }.getOrDefault(listOf(input))
        } else {
            input.split(",").map { it.trim() }
        }
        val output = StringBuilder()
        paths.forEach { path ->
            runCatching {
                val file = resolvePathOrThrow(ideService, path)
                val content = ideService.getFileContent(file.absolutePath, null, null).orEmpty()
                output.append("--- FILE: ").append(path).append(" ---\n")
                output.append(content).append("\n\n")
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
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val content = requireString(args, "content")
        val file = resolvePathOrThrow(ideService, filePath)
        val msg = showPatchAndApply(ideService, file, content, "Review Gemini file update", refreshAfterApply = false)
        return textResult(msg)
    }
}

class ListFilesTool : BaseMcpTool() {
    override fun getName(): String = "listFiles"
    override fun getDescription(): String = "Lists the contents of a directory. Use this to explore the project structure if 'getProjectSummary' or 'getProjectStructure' didn't provide enough detail."
    override fun getRequiredParams(): Map<String, String> = mapOf("directoryPath" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("recursive" to "boolean", "maxFiles" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val dirPath = requireString(args, "directoryPath")
        val dir = resolvePathOrThrow(ideService, dirPath)
        val recursive = optionalBoolean(args, "recursive")
        val maxFiles = (optionalInt(args, "maxFiles") ?: 500).coerceIn(1, 5000)
        val files = ideService.listFiles(dir, recursive, maxFiles)
        return textResult(files.joinToString("\n"))
    }
}

class OpenFileTool : BaseMcpTool() {
    override fun getName(): String = "openFile"
    override fun getDescription(): String = "Opens a file in an editor tab. Use this to focus the user's attention on a specific file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(ideService, filePath)
        ideService.openFile(file)
        return textResult("opened ${file.absolutePath}")
    }
}

class CreateFileTool : BaseMcpTool() {
    override fun getName(): String = "createFile"
    override fun getDescription(): String = "Creates a new file on disk. Use this when starting new modules or adding assets."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("content" to "string")
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
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val sourcePath = requireString(args, "sourcePath")
        val destPath = requireString(args, "destPath")
        val result = ideService.renameFile(sourcePath, destPath)
        return textResult(result)
    }
}
