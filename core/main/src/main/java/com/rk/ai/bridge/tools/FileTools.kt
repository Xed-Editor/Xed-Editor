package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class ReadFileTool : BaseMcpTool() {
    override val name: String = "readFile"
    override val description: String = "Reads the full content of a file. Use this for deep analysis of a single file. For multiple files, use 'readFiles' instead."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val file = safeResolvePath(context, filePath)
        val content = context.ideService.getFileContent(file.absolutePath).orEmpty()
        return resultText(content)
    }
}

class ReadFilesTool : BaseMcpTool() {
    override val name: String = "readFiles"
    override val description: String = "RECOMMENDED: Reads multiple files at once. Use this to gather context across several related files in a single turn. Input 'filePaths' can be a comma-separated string or a JSON array of strings."
    override val requiredParams: Map<String, String> = mapOf("filePaths" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val input = requireString(args, "filePaths")
        val paths = if (input.startsWith("[")) {
            runCatching { com.google.gson.JsonParser.parseString(input).asJsonArray.map { it.asString } }.getOrDefault(listOf(input))
        } else {
            input.split(",").map { it.trim() }
        }
        val output = StringBuilder()
        paths.forEach { path ->
            runCatching {
                val file = safeResolvePath(context, path)
                val content = context.ideService.getFileContent(file.absolutePath).orEmpty()
                output.append("--- FILE: ").append(path).append(" ---\n")
                output.append(content).append("\n\n")
            }.onFailure {
                output.append("--- FILE: ").append(path).append(" (ERROR: ").append(it.message).append(") ---\n\n")
            }
        }
        return resultText(output.toString())
    }
}

class WriteFileTool : BaseMcpTool() {
    override val name: String = "writeFile"
    override val description: String = "Writes new content to a file. Use this for single-file updates. For cross-file changes, prefer 'applyBatchEdits'. Opens a review tab for the user."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string", "content" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val content = requireString(args, "content")
        val file = safeResolvePath(context, filePath)
        val msg = showPatchAndApply(context, file, content, "Review Gemini file update", refreshAfterApply = false)
        return resultText(msg)
    }
}

class ListFilesTool : BaseMcpTool() {
    override val name: String = "listFiles"
    override val description: String = "Lists the contents of a directory. Use this to explore the project structure if 'getProjectSummary' or 'getProjectStructure' didn't provide enough detail."
    override val requiredParams: Map<String, String> = mapOf("directoryPath" to "string")
    override val optionalParams: Map<String, String> = mapOf("recursive" to "boolean", "maxFiles" to "number")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val dirPath = requireString(args, "directoryPath")
        val dir = safeResolvePath(context, dirPath)
        val recursive = optionalBoolean(args, "recursive")
        val maxFiles = optionalInt(args, "maxFiles", 500).coerceIn(1, 5000)
        val files = context.ideService.listFiles(dir, recursive, maxFiles)
        return resultText(files.joinToString("\n"))
    }
}

class OpenFileTool : BaseMcpTool() {
    override val name: String = "openFile"
    override val description: String = "Opens a file in an editor tab. Use this to focus the user's attention on a specific file."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val file = safeResolvePath(context, filePath)
        context.ideService.openFile(file)
        return resultText("opened ${file.absolutePath}")
    }
}

class CreateFileTool : BaseMcpTool() {
    override val name: String = "createFile"
    override val description: String = "Creates a new file on disk. Use this when starting new modules or adding assets."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string")
    override val optionalParams: Map<String, String> = mapOf("content" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val content = optionalString(args, "content")
        val result = context.ideService.createFile(filePath, content.ifBlank { null })
        return resultText(result)
    }
}

class DeleteFileTool : BaseMcpTool() {
    override val name: String = "deleteFile"
    override val description: String = "Deletes a file from the workspace. Use with caution."
    override val requiredParams: Map<String, String> = mapOf("filePath" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = requireString(args, "filePath")
        val result = context.ideService.deleteFile(filePath)
        return resultText(result)
    }
}

class RenameFileTool : BaseMcpTool() {
    override val name: String = "renameFile"
    override val description: String = "Moves or renames a file. Updates disk state immediately."
    override val requiredParams: Map<String, String> = mapOf("sourcePath" to "string", "destPath" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val sourcePath = requireString(args, "sourcePath")
        val destPath = requireString(args, "destPath")
        val result = context.ideService.renameFile(sourcePath, destPath)
        return resultText(result)
    }
}
