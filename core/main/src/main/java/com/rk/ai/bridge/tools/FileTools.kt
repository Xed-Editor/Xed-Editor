package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService
import java.io.File

class ReadFileTool : McpTool {
    override fun getName(): String = "readFile"
    override fun getDescription(): String = "Reads the full content of a file at the given path."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        val file = ideService.resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace")
        val content = ideService.getFileContent(file.absolutePath).orEmpty()
        return textResult(content)
    }
}

class WriteFileTool : McpTool {
    override fun getName(): String = "writeFile"
    override fun getDescription(): String = "Opens a diff review for writing new content to a file."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string", "content" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        val content = args.get("content")?.asString.orEmpty()
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        val file = ideService.resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace")
        
        val oldContent = ideService.getFileContent(file.absolutePath) ?: runCatching { file.readText() }.getOrDefault("")
        
        ideService.showPatch(file.absolutePath, oldContent, content, "Review Gemini file update") {
            ideService.writeFile(file, content)
        }
        
        return textResult("File update opened in Xed Editor for ${file.absolutePath}. Results will be sent via notifications.")
    }
}

class ListFilesTool : McpTool {
    override fun getName(): String = "listFiles"
    override fun getDescription(): String = "Lists files and directories in a directory."
    override fun getRequiredParams(): Map<String, String> = mapOf("directoryPath" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("recursive" to "boolean", "maxFiles" to "number")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val dirPath = args.get("directoryPath")?.asString.orEmpty()
        val dir = ideService.resolvePath(dirPath) ?: throw IllegalArgumentException("path outside workspace")
        val recursive = args.get("recursive")?.asBoolean ?: false
        val maxFiles = args.get("maxFiles")?.asInt ?: 500
        val files = ideService.listFiles(dir, recursive, maxFiles.coerceIn(1, 5000))
        return textResult(files.joinToString("\n"))
    }
}

class OpenFileTool : McpTool {
    override fun getName(): String = "openFile"
    override fun getDescription(): String = "Opens a file in the editor tab."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        val file = ideService.resolvePath(filePath) ?: throw IllegalArgumentException("path outside workspace")
        ideService.openFile(file)
        return textResult("opened ${file.absolutePath}")
    }
}

class CreateFileTool : McpTool {
    override fun getName(): String = "createFile"
    override fun getDescription(): String = "Creates a new file with optional initial content."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("content" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        val content = args.get("content")?.asString
        val result = ideService.createFile(filePath, content)
        return textResult(result)
    }
}

class DeleteFileTool : McpTool {
    override fun getName(): String = "deleteFile"
    override fun getDescription(): String = "Permanently deletes a file from the workspace."
    override fun getRequiredParams(): Map<String, String> = mapOf("filePath" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = args.get("filePath")?.asString.orEmpty()
        if (filePath.isBlank()) throw IllegalArgumentException("filePath required")
        val result = ideService.deleteFile(filePath)
        return textResult(result)
    }
}

class RenameFileTool : McpTool {
    override fun getName(): String = "renameFile"
    override fun getDescription(): String = "Renames or moves a file from source to destination."
    override fun getRequiredParams(): Map<String, String> = mapOf("sourcePath" to "string", "destPath" to "string")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val sourcePath = args.get("sourcePath")?.asString.orEmpty()
        val destPath = args.get("destPath")?.asString.orEmpty()
        if (sourcePath.isBlank()) throw IllegalArgumentException("sourcePath required")
        if (destPath.isBlank()) throw IllegalArgumentException("destPath required")
        val result = ideService.renameFile(sourcePath, destPath)
        return textResult(result)
    }
}
