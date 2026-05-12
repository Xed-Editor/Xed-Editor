package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService
import java.io.File

class ReadFileTool : BaseMcpTool() {
    override fun getName(): String = "readFile"
    override fun getDescription(): String = "Reads the full content of a file at the given path."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val file = resolvePathOrThrow(ideService, filePath)
        val content = ideService.getFileContent(file.absolutePath).orEmpty()
        return textResult(content)
    }
}

class WriteFileTool : BaseMcpTool() {
    override fun getName(): String = "writeFile"
    override fun getDescription(): String = "Opens a diff review for writing new content to a file."
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
    override fun getDescription(): String = "Lists files and directories in a directory."
    override fun getRequiredParams(): Map<String, String> = mapOf("directoryPath" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("recursive" to "boolean", "maxFiles" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val dirPath = requireString(args, "directoryPath")
        val dir = resolvePathOrThrow(ideService, dirPath)
        val recursive = optionalBoolean(args, "recursive")
        val maxFiles = optionalInt(args, "maxFiles", 500).coerceIn(1, 5000)
        val files = ideService.listFiles(dir, recursive, maxFiles)
        return textResult(files.joinToString("\n"))
    }
}

class OpenFileTool : BaseMcpTool() {
    override fun getName(): String = "openFile"
    override fun getDescription(): String = "Opens a file in the editor tab."
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
    override fun getDescription(): String = "Creates a new file with optional initial content."
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
    override fun getDescription(): String = "Permanently deletes a file from the workspace."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = requireString(args, "filePath")
        val result = ideService.deleteFile(filePath)
        return textResult(result)
    }
}

class RenameFileTool : BaseMcpTool() {
    override fun getName(): String = "renameFile"
    override fun getDescription(): String = "Renames or moves a file from source to destination."
    override fun getRequiredParams(): Map<String, String> = mapOf("sourcePath" to "string", "destPath" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val sourcePath = requireString(args, "sourcePath")
        val destPath = requireString(args, "destPath")
        val result = ideService.renameFile(sourcePath, destPath)
        return textResult(result)
    }
}
