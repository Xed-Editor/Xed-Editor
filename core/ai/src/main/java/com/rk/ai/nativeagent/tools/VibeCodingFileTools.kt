package com.rk.ai.nativeagent.tools

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService
import java.io.File

class VibeCodingFileTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(readFile, writeFile, editFile, createFile, deleteFile, renameFile, listFiles)

    private val readFile = Tool(
        name = "readFile",
        description = "Read the contents of a file. Supports line range with startLine and endLine (1-indexed, inclusive). Returns file content truncated at 250KB.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("path", "Absolute path or workspace-relative path to the file")
                    add("startLine", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "First line to read (1-indexed)") })
                    add("endLine", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Last line to read (inclusive)") })
                },
                required = listOf("path"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing path argument"))
            val startLine = obj["startLine"]?.asJsonPrimitive?.asInt
            val endLine = obj["endLine"]?.asJsonPrimitive?.asInt
            val content = ideService.getFileContent(path, startLine, endLine)
            if (content != null) {
                listOf(UIMessagePart.Text(content))
            } else {
                listOf(UIMessagePart.Text("File not found: $path"))
            }
        },
    )

    private val writeFile = Tool(
        name = "writeFile",
        description = "Write content to a file. Creates parent directories if needed. Opens a review tab for the user to confirm.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("path", "Absolute path to the file")
                    addProperty("content", "The full content to write")
                },
                required = listOf("path", "content"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing path argument"))
            val content = obj["content"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing content argument"))
            ideService.writeFile(File(path), content)
            listOf(UIMessagePart.Text("Written to $path"))
        },
    )

    private val editFile = Tool(
        name = "editFile",
        description = "Surgically replace text in a file using exact string matching. If oldString is found multiple times, provide more context. Use replaceAll to change all occurrences.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("filePath", "Absolute path to the file")
                    addProperty("oldString", "The exact text to find and replace")
                    addProperty("newString", "The replacement text")
                    addProperty("replaceAll", "Boolean - replace all occurrences if true")
                    addProperty("dryRun", "Boolean - if true, only report whether the edit would succeed")
                    addProperty("partialMatch", "Boolean - allow matching suffix/prefix if exact match fails")
                },
                required = listOf("filePath", "oldString", "newString"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val filePath = obj["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            val oldString = obj["oldString"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing oldString"))
            val newString = obj["newString"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing newString"))
            val content = ideService.getFileContent(filePath, null, null) ?: return@Tool listOf(UIMessagePart.Text("File not found: $filePath"))
            if (content.contains(oldString)) {
                val result = content.replace(oldString, newString)
                ideService.writeFile(File(filePath), result)
                listOf(UIMessagePart.Text("Edited $filePath"))
            } else {
                listOf(UIMessagePart.Text("Could not find the specified text in $filePath"))
            }
        },
    )

    private val createFile = Tool(
        name = "createFile",
        description = "Create a new file with optional content. Creates parent directories automatically.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("filePath", "Absolute path for the new file")
                    addProperty("content", "Initial file content (optional)")
                },
                required = listOf("filePath"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val filePath = obj["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            val content = obj["content"]?.asJsonPrimitive?.asString
            val result = ideService.createFile(filePath, content)
            listOf(UIMessagePart.Text(result))
        },
    )

    private val deleteFile = Tool(
        name = "deleteFile",
        description = "Delete a file from the workspace.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("filePath", "Absolute path of the file to delete")
                },
                required = listOf("filePath"),
            )
        },
        execute = { args ->
            val filePath = args.asJsonObject["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            val result = ideService.deleteFile(filePath)
            listOf(UIMessagePart.Text(result))
        },
    )

    private val renameFile = Tool(
        name = "renameFile",
        description = "Rename or move a file or directory.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("sourcePath", "Current path of the file or directory")
                    addProperty("destPath", "New path for the file or directory")
                },
                required = listOf("sourcePath", "destPath"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val sourcePath = obj["sourcePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing sourcePath"))
            val destPath = obj["destPath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing destPath"))
            val result = ideService.renameFile(sourcePath, destPath)
            listOf(UIMessagePart.Text(result))
        },
    )

    private val listFiles = Tool(
        name = "listFiles",
        description = "List files in a directory. Supports recursive listing with maxFiles limit.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("path", "Directory path to list")
                    add("recursive", JsonObject().apply { addProperty("type", "boolean"); addProperty("description", "List files recursively") })
                    add("maxFiles", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Maximum files to return") })
                },
                required = listOf("path"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing path"))
            val recursive = obj["recursive"]?.asJsonPrimitive?.asBoolean ?: false
            val maxFiles = obj["maxFiles"]?.asJsonPrimitive?.asInt ?: 200
            val file = ideService.resolvePath(path)
            if (file != null && file.isDirectory) {
                val entries = ideService.listFiles(file, recursive, maxFiles)
                listOf(UIMessagePart.Text(entries.joinToString("\n")))
            } else {
                listOf(UIMessagePart.Text("Directory not found: $path"))
            }
        },
    )
}
