@file:OptIn(ExperimentalUuidApi::class)

package com.rk.ai.agent.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService
import java.io.File

class VibeCodingFileTools(private val ideService: IdeService) {
    companion object {
        private fun extractPath(obj: com.google.gson.JsonObject): String? {
            return obj["path"]?.asJsonPrimitive?.asString
                ?: obj["filePath"]?.asJsonPrimitive?.asString
                ?: obj["file"]?.asJsonPrimitive?.asString
        }

        fun parseFilePaths(element: com.google.gson.JsonElement?): List<String> {
            if (element == null) return emptyList()

            if (element is JsonArray) {
                return element.mapNotNull { it.asJsonPrimitive?.asString?.trim() }.filter { it.isNotBlank() }
            }

            val raw = element.asJsonPrimitive?.asString?.trim() ?: return emptyList()
            if (raw.isBlank()) return emptyList()

            if (raw.startsWith("[")) {
                return runCatching {
                    val arr = JsonParser.parseString(raw).asJsonArray
                    arr.mapNotNull { it.asJsonPrimitive?.asString?.trim() }.filter { it.isNotBlank() }
                }.getOrDefault(
                    raw.removeSurrounding("[", "]").split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotBlank() }
                )
            }

            return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    private val readFile = Tool(
        name = "readFile",
        description = "Read the contents of a file. Supports startLine/endLine (1-indexed, inclusive). Content truncated at 250KB.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Absolute or workspace-relative path to the file") }
                    putJsonObject("startLine") { put("type", "integer"); put("description", "First line to read (1-indexed)") }
                    putJsonObject("endLine") { put("type", "integer"); put("description", "Last line to read (inclusive)") }
                },
                required = listOf("path"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val rawPath = obj["path"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing path argument"))
            val startLine = obj["startLine"]?.asJsonPrimitive?.asInt
            val endLine = obj["endLine"]?.asJsonPrimitive?.asInt
            val resolved = ideService.resolvePath(rawPath)
            val filePath = resolved?.absolutePath ?: rawPath
            val content = ideService.getFileContent(filePath, startLine, endLine)
            if (content != null) {
                listOf(UIMessagePart.Text(content))
            } else {
                listOf(UIMessagePart.Text("File not found: $rawPath"))
            }
        },
    )

    private val cat = Tool(
        name = "cat",
        description = "Alias for readFile. Same as readFile. Accepts: path, filePath, file.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Absolute or relative path to the file") }
                    putJsonObject("filePath") { put("type", "string"); put("description", "Alternative to path") }
                    putJsonObject("file") { put("type", "string"); put("description", "Alternative to path") }
                    putJsonObject("startLine") { put("type", "integer") }
                    putJsonObject("endLine") { put("type", "integer") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val rawPath = extractPath(obj) ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file argument"))
            val startLine = obj["startLine"]?.asJsonPrimitive?.asInt
            val endLine = obj["endLine"]?.asJsonPrimitive?.asInt
            val resolved = ideService.resolvePath(rawPath)
            val filePath = resolved?.absolutePath ?: rawPath
            val content = ideService.getFileContent(filePath, startLine, endLine)
            if (content != null) listOf(UIMessagePart.Text(content))
            else listOf(UIMessagePart.Text("File not found: $rawPath"))
        },
    )

    private val readFiles = Tool(
        name = "readFiles",
        description = "RECOMMENDED: Reads multiple files at once. Input can be comma-separated paths or JSON array of path strings.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("filePaths") { put("type", "string"); put("description", "Comma-separated list of paths or JSON array of path strings") }
                },
                required = listOf("filePaths"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val paths = parseFilePaths(obj["filePaths"])
            if (paths.isEmpty()) return@Tool listOf(UIMessagePart.Text("Missing filePaths argument"))
            val results = paths.map { rawPath ->
                val resolved = ideService.resolvePath(rawPath)
                val filePath = resolved?.absolutePath ?: rawPath
                val content = ideService.getFileContent(filePath, null, null)
                if (content != null) "--- $rawPath ---\n$content"
                else "--- $rawPath ---\n(FILE NOT FOUND)"
            }
            listOf(UIMessagePart.Text(results.joinToString("\n\n")))
        },
    )

    private val writeFile = Tool(
        name = "writeFile",
        description = "Write content to a file. Creates parent directories if needed. Opens a review tab for the user to confirm.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Absolute path to the file") }
                    putJsonObject("content") { put("type", "string"); put("description", "The full content to write") }
                },
                required = listOf("path", "content"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing path argument"))
            val content = obj["content"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing content argument"))
            val file = ideService.resolvePath(path)
            if (file == null) return@Tool listOf(UIMessagePart.Text("Path could not be resolved: $path"))
            ideService.writeFile(file, content)
            listOf(UIMessagePart.Text("Written to ${file.absolutePath}"))
        },
    )

    private val editFile = Tool(
        name = "editFile",
        description = "Surgically replace text in a file using exact string matching. PREFERRED for targeted changes. Supports dryRun, partialMatch, and replaceAll.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("filePath") { put("type", "string"); put("description", "Absolute path to the file") }
                    putJsonObject("oldString") { put("type", "string"); put("description", "The exact text to find and replace") }
                    putJsonObject("newString") { put("type", "string"); put("description", "The replacement text") }
                    putJsonObject("replaceAll") { put("type", "boolean"); put("description", "Replace all occurrences if true") }
                    putJsonObject("dryRun") { put("type", "boolean"); put("description", "Only report whether the edit would succeed") }
                    putJsonObject("partialMatch") { put("type", "boolean"); put("description", "Allow matching suffix/prefix if exact match fails") }
                },
                required = listOf("filePath", "oldString", "newString"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val filePath = obj["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            val oldString = obj["oldString"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing oldString"))
            val newString = obj["newString"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing newString"))
            val dryRun = obj["dryRun"]?.asJsonPrimitive?.asBoolean ?: false
            val partialMatch = obj["partialMatch"]?.asJsonPrimitive?.asBoolean ?: false
            val replaceAll = obj["replaceAll"]?.asJsonPrimitive?.asBoolean ?: false
            val file = ideService.resolvePath(filePath)
            if (file == null) return@Tool listOf(UIMessagePart.Text("Path could not be resolved: $filePath"))
            val resolvedPath = file.absolutePath
            val content = ideService.getFileContent(resolvedPath, null, null) ?: return@Tool listOf(UIMessagePart.Text("File not found: $resolvedPath"))

            // Count occurrences of oldString
            var matchCount = 0
            var searchIdx = 0
            while (true) {
                searchIdx = content.indexOf(oldString, searchIdx)
                if (searchIdx == -1) break
                matchCount++
                searchIdx += oldString.length
            }

            // Handle exact match not found
            if (matchCount == 0) {
                if (partialMatch) {
                    val partialIdx = content.indexOf(oldString.take(minOf(oldString.length, 30)))
                    if (partialIdx != -1) {
                        if (dryRun) return@Tool listOf(UIMessagePart.Text("[dry-run] Would edit $resolvedPath via partial match"))
                        val result = content.replace(oldString, newString)
                        ideService.writeFile(file, result)
                        return@Tool listOf(UIMessagePart.Text("Edited $resolvedPath (partial match)"))
                    }
                }
                return@Tool listOf(UIMessagePart.Text("Could not find the specified text in $resolvedPath"))
            }

            // Handle multiple matches without replaceAll
            if (matchCount > 1 && !replaceAll) {
                return@Tool listOf(UIMessagePart.Text(
                    "Found $matchCount matches in $resolvedPath. " +
                    "Provide more surrounding context in oldString to identify the correct match, or use replaceAll=true."
                ))
            }

            // Dry run
            if (dryRun) {
                val mode = if (replaceAll) "replaceAll" else "single"
                return@Tool listOf(UIMessagePart.Text("[dry-run] Would edit $resolvedPath ($mode: ${oldString.length} chars -> ${newString.length} chars)"))
            }

            // Execute edit
            val result = content.replace(oldString, newString)
            ideService.writeFile(file, result)

            val note = when {
                replaceAll -> " (replaced all $matchCount occurrences)"
                matchCount == 1 -> ""
                else -> ""
            }
            listOf(UIMessagePart.Text("Edited $resolvedPath$note"))
        },
    )

    private val multiEditFile = Tool(
        name = "multiEditFile",
        description = "RECOMMENDED for targeted edits. Replace multiple non-contiguous blocks of text in a single file atomically. Provide an array of edits with oldString and newString. Fails if any oldString is not found exactly once.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("filePath") { put("type", "string"); put("description", "Absolute path to the file") }
                    putJsonObject("edits") { 
                        put("type", "array")
                        put("description", "Array of objects containing oldString and newString")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("oldString") { put("type", "string"); put("description", "Exact text to replace") }
                                putJsonObject("newString") { put("type", "string"); put("description", "Replacement text") }
                            }
                            putJsonArray("required") {
                                add(kotlinx.serialization.json.JsonPrimitive("oldString"))
                                add(kotlinx.serialization.json.JsonPrimitive("newString"))
                            }
                        }
                    }
                },
                required = listOf("filePath", "edits"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val filePath = obj["filePath"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing filePath"))
            val editsArr = obj["edits"]?.asJsonArray ?: return@Tool listOf(UIMessagePart.Text("Missing edits array"))
            
            val file = ideService.resolvePath(filePath)
            if (file == null) return@Tool listOf(UIMessagePart.Text("Path could not be resolved: $filePath"))
            
            var content = ideService.getFileContent(file.absolutePath, null, null) 
                ?: return@Tool listOf(UIMessagePart.Text("File not found: ${file.absolutePath}"))
            
            var successCount = 0
            var errorCount = 0
            val errors = mutableListOf<String>()
            
            for (i in 0 until editsArr.size()) {
                val editObj = editsArr[i].asJsonObject
                val oldString = editObj["oldString"]?.asJsonPrimitive?.asString ?: ""
                val newString = editObj["newString"]?.asJsonPrimitive?.asString ?: ""
                
                if (oldString.isEmpty()) continue
                
                val matchCount = content.split(oldString).size - 1
                if (matchCount == 0) {
                    errorCount++
                    errors.add("Edit ${i+1}: Could not find exact text to replace:\n```\n${oldString.take(200)}\n```")
                } else if (matchCount > 1) {
                    errorCount++
                    val lines = oldString.lines()
                    val hint = if (lines.size <= 2) {
                        "\nThe text appears $matchCount times. Add more surrounding lines to make it unique."
                    } else {
                        val firstLine = lines.first().trim()
                        val lastLine = lines.last().trim()
                        "\nFound $matchCount matches. Try including unique lines like:\n  First: \"$firstLine\"\n  Last: \"$lastLine\""
                    }
                    errors.add("Edit ${i+1}: Found $matchCount matches for oldString.$hint")
                } else {
                    content = content.replaceFirst(oldString, newString)
                    successCount++
                }
            }
            
            if (successCount > 0 && errorCount == 0) {
                ideService.writeFile(file, content)
                listOf(UIMessagePart.Text("Successfully applied $successCount edits to ${file.absolutePath}"))
            } else if (successCount > 0 && errorCount > 0) {
                listOf(UIMessagePart.Text("Failed to apply all edits. No changes were written to disk. Errors:\n" + errors.joinToString("\n")))
            } else {
                listOf(UIMessagePart.Text("Failed to apply any edits. No changes were written to disk. Errors:\n" + errors.joinToString("\n")))
            }
        }
    )

    private val applyBatchEdits = Tool(
        name = "applyBatchEdits",
        description = "RECOMMENDED: Applies multiple file changes at once. ALWAYS use this for cross-file refactorings to ensure consistency and minimize turns. Takes a JSON object where keys are absolute file paths and values are new content.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("edits") { put("type", "string"); put("description", "JSON object mapping file paths to their new content: {\"path/to/file.kt\": \"new content...\"}") }
                },
                required = listOf("edits"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val editsElement = obj["edits"] ?: return@Tool listOf(UIMessagePart.Text("Missing edits argument (must be a JSON object)"))
            val editsObj = when {
                editsElement.isJsonObject -> editsElement.asJsonObject
                editsElement.isJsonPrimitive && editsElement.asJsonPrimitive.isString -> {
                    try {
                        com.google.gson.JsonParser.parseString(editsElement.asString).asJsonObject
                    } catch (e: Exception) {
                        return@Tool listOf(UIMessagePart.Text("Invalid JSON string in 'edits': ${e.message}"))
                    }
                }
                else -> return@Tool listOf(UIMessagePart.Text("'edits' must be a JSON object or a JSON string"))
            }
            val edits = mutableMapOf<String, String>()
            editsObj.entrySet().forEach { entry ->
                val path = entry.key
                val content = when {
                    entry.value.isJsonPrimitive -> entry.value.asString
                    entry.value.isJsonObject -> entry.value.toString()
                    else -> entry.value.toString()
                }
                val file = ideService.resolvePath(path)
                edits[file?.absolutePath ?: path] = content
            }
            ideService.applyBatchEdits(edits)
            listOf(UIMessagePart.Text("Batch edits for ${edits.size} files applied."))
        },
    )

    private val createFile = Tool(
        name = "createFile",
        description = "Create a new file with optional initial content. Creates parent directories automatically.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("filePath") { put("type", "string"); put("description", "Absolute path for the new file") }
                    putJsonObject("content") { put("type", "string"); put("description", "Initial file content (optional)") }
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
                properties = buildJsonObject {
                    putJsonObject("filePath") { put("type", "string"); put("description", "Absolute path of the file to delete") }
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
        description = "Rename or move a file or directory to a new workspace path.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("sourcePath") { put("type", "string"); put("description", "Current path of the file or directory") }
                    putJsonObject("destPath") { put("type", "string"); put("description", "New path for the file or directory") }
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
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Directory path to list") }
                    putJsonObject("recursive") { put("type", "boolean"); put("description", "List files recursively (default: false)") }
                    putJsonObject("maxFiles") { put("type", "integer"); put("description", "Maximum number of files to return (default: 500, max: 5000)") }
                },
                required = listOf("path"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString ?: return@Tool listOf(UIMessagePart.Text("Missing path"))
            val recursive = obj["recursive"]?.asJsonPrimitive?.asBoolean ?: false
            val maxFiles = (obj["maxFiles"]?.asJsonPrimitive?.asInt ?: 500).coerceIn(1, 5000)
            val file = ideService.resolvePath(path)
            if (file != null && file.isDirectory) {
                val entries = ideService.listFiles(file, recursive, maxFiles)
                listOf(UIMessagePart.Text(entries.joinToString("\n")))
            } else {
                listOf(UIMessagePart.Text("Directory not found: $path"))
            }
        },
    )

    private val ls = Tool(
        name = "ls",
        description = "Same as listFiles. Lists directory contents. Accepts: path, directoryPath.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Directory path to list") }
                    putJsonObject("directoryPath") { put("type", "string"); put("description", "Alternative to path") }
                    putJsonObject("recursive") { put("type", "boolean"); put("description", "List files recursively (default: false)") }
                    putJsonObject("maxFiles") { put("type", "integer"); put("description", "Maximum number of files to return (default: 500, max: 5000)") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString
                ?: obj["directoryPath"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing path/directoryPath"))
            val recursive = obj["recursive"]?.asJsonPrimitive?.asBoolean ?: false
            val maxFiles = (obj["maxFiles"]?.asJsonPrimitive?.asInt ?: 500).coerceIn(1, 5000)
            val file = ideService.resolvePath(path)
            if (file != null && file.isDirectory) {
                val entries = ideService.listFiles(file, recursive, maxFiles)
                listOf(UIMessagePart.Text(entries.joinToString("\n")))
            } else {
                listOf(UIMessagePart.Text("Directory not found: $path"))
            }
        },
    )

    private val findFiles = Tool(
        name = "findFiles",
        description = "Finds files by glob patterns like '*.kt' or '**/*.java'. Accepts: query, pattern, limit, path.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("query") { put("type", "string"); put("description", "File name or glob pattern to search for (e.g. *.kt, **/*.java)") }
                    putJsonObject("pattern") { put("type", "string"); put("description", "Alternative to query") }
                    putJsonObject("limit") { put("type", "integer"); put("description", "Maximum results (default: 100)") }
                    putJsonObject("path") { put("type", "string"); put("description", "Directory to search in (default: workspace root)") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val query = obj["query"]?.asJsonPrimitive?.asString
                ?: obj["pattern"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing query/pattern"))
            val limit = obj["limit"]?.asJsonPrimitive?.asInt ?: 100
            val rawPath = obj["path"]?.asJsonPrimitive?.asString
            val resolvedDir = if (rawPath != null) ideService.resolvePath(rawPath)?.absolutePath else null
            val results = ideService.findFiles(query, limit, resolvedDir ?: rawPath)
            if (results.size() > 0) {
                val text = results.joinToString("\n") { element ->
                    when {
                        element.isJsonObject -> {
                            val path = element.asJsonObject["path"]?.asString ?: element.toString()
                            val name = element.asJsonObject["name"]?.asString
                            if (name != null) "$path ($name)" else path
                        }
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }
                }
                listOf(UIMessagePart.Text(text))
            } else {
                listOf(UIMessagePart.Text("No files found matching: $query"))
            }
        },
    )

    private val glob = Tool(
        name = "glob",
        description = "Alias for findFiles. Finds files by glob patterns. Accepts: query, pattern, limit, path.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("query") { put("type", "string"); put("description", "File name or glob pattern to search for (e.g. *.kt, **/*.java)") }
                    putJsonObject("pattern") { put("type", "string"); put("description", "Alternative to query") }
                    putJsonObject("limit") { put("type", "integer"); put("description", "Maximum results (default: 100)") }
                    putJsonObject("path") { put("type", "string"); put("description", "Directory to search in (default: workspace root)") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val query = obj["query"]?.asJsonPrimitive?.asString
                ?: obj["pattern"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing query/pattern"))
            val limit = obj["limit"]?.asJsonPrimitive?.asInt ?: 100
            val rawPath = obj["path"]?.asJsonPrimitive?.asString
            val resolvedDir = if (rawPath != null) ideService.resolvePath(rawPath)?.absolutePath else null
            val results = ideService.findFiles(query, limit, resolvedDir ?: rawPath)
            if (results.size() > 0) {
                val text = results.joinToString("\n") { element ->
                    when {
                        element.isJsonObject -> {
                            val path = element.asJsonObject["path"]?.asString ?: element.toString()
                            val name = element.asJsonObject["name"]?.asString
                            if (name != null) "$path ($name)" else path
                        }
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }
                }
                listOf(UIMessagePart.Text(text))
            } else {
                listOf(UIMessagePart.Text("No files found matching: $query"))
            }
        },
    )

    private val head = Tool(
        name = "head",
        description = "Reads first N lines of a file. Accepts: path, filePath, file, lines, count.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Absolute or relative path to the file") }
                    putJsonObject("filePath") { put("type", "string"); put("description", "Alternative to path") }
                    putJsonObject("file") { put("type", "string"); put("description", "Alternative to path") }
                    putJsonObject("lines") { put("type", "integer"); put("description", "Number of lines to read from the top (default: 10, max: 10000)") }
                    putJsonObject("count") { put("type", "integer"); put("description", "Alias for lines") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val rawPath = extractPath(obj) ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file"))
            val n = (obj["lines"]?.asJsonPrimitive?.asInt ?: obj["count"]?.asJsonPrimitive?.asInt ?: 10).coerceIn(1, 10000)
            val resolved = ideService.resolvePath(rawPath)
            val filePath = resolved?.absolutePath ?: rawPath
            val content = ideService.getFileContent(filePath, 1, n)
            if (content != null) listOf(UIMessagePart.Text(content))
            else listOf(UIMessagePart.Text("File not found: $rawPath"))
        },
    )

    private val tail = Tool(
        name = "tail",
        description = "Reads last N lines of a file. Accepts: path, filePath, file, lines, count.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Absolute or relative path to the file") }
                    putJsonObject("filePath") { put("type", "string"); put("description", "Alternative to path") }
                    putJsonObject("file") { put("type", "string"); put("description", "Alternative to path") }
                    putJsonObject("lines") { put("type", "integer"); put("description", "Number of lines to read from the bottom (default: 10, max: 10000)") }
                    putJsonObject("count") { put("type", "integer"); put("description", "Alias for lines") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val rawPath = extractPath(obj) ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file"))
            val n = (obj["lines"]?.asJsonPrimitive?.asInt ?: obj["count"]?.asJsonPrimitive?.asInt ?: 10).coerceIn(1, 10000)
            val resolved = ideService.resolvePath(rawPath)
            val filePath = resolved?.absolutePath ?: rawPath
            val fullContent = ideService.getFileContent(filePath, null, null)
            if (fullContent == null) return@Tool listOf(UIMessagePart.Text("File not found: $rawPath"))
            val lines = fullContent.split("\n")
            val tailLines = lines.takeLast(n)
            listOf(UIMessagePart.Text(tailLines.joinToString("\n")))
        },
    )

    private val wc = Tool(
        name = "wc",
        description = "Counts lines/words/chars/bytes. Accepts: path, filePath, file.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Absolute or relative path to the file") }
                    putJsonObject("filePath") { put("type", "string"); put("description", "Alternative to path") }
                    putJsonObject("file") { put("type", "string"); put("description", "Alternative to path") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = extractPath(obj) ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file"))
            val file = ideService.resolvePath(path) ?: return@Tool listOf(UIMessagePart.Text("Path not found: $path"))
            val text = ideService.getFileContent(file.absolutePath, null, null)
                ?: return@Tool listOf(UIMessagePart.Text("File not found: ${file.absolutePath}"))
            val lines = if (text.isEmpty()) 0L else {
                val count = text.count { it == '\n' }.toLong()
                if (text.last() != '\n') count + 1 else count
            }
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }.toLong()
            val chars = text.length.toLong()
            val bytes = text.encodeToByteArray().size.toLong()
            val result = JsonObject().apply {
                addProperty("lines", lines)
                addProperty("words", words)
                addProperty("characters", chars)
                addProperty("bytes", bytes)
                addProperty("path", file.absolutePath)
            }.toString()
            listOf(UIMessagePart.Text(result))
        },
    )

    private val countLines = Tool(
        name = "countLines",
        description = "Fast buffered byte-level line counting. Accepts: path, filePath, file.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Absolute or relative path to the file") }
                    putJsonObject("filePath") { put("type", "string"); put("description", "Alternative to path") }
                    putJsonObject("file") { put("type", "string"); put("description", "Alternative to path") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = extractPath(obj) ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file"))
            val file = ideService.resolvePath(path) ?: return@Tool listOf(UIMessagePart.Text("Path not found: $path"))
            val text = ideService.getFileContent(file.absolutePath, null, null)
                ?: return@Tool listOf(UIMessagePart.Text("File not found: ${file.absolutePath}"))
            val lines = if (text.isEmpty()) 0L else text.count { it == '\n' }.toLong()
            val result = JsonObject().apply {
                addProperty("lines", lines)
                addProperty("path", file.absolutePath)
            }.toString()
            listOf(UIMessagePart.Text(result))
        },
    )

    private val stat = Tool(
        name = "stat",
        description = "Gets file metadata (size, permissions, modified time). Accepts: path, filePath, file.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Absolute or relative path to the file") }
                    putJsonObject("filePath") { put("type", "string"); put("description", "Alternative to path") }
                    putJsonObject("file") { put("type", "string"); put("description", "Alternative to path") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = extractPath(obj) ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file"))
            val file = ideService.resolvePath(path) ?: return@Tool listOf(UIMessagePart.Text("Path not found: $path"))
            val exists = file.exists()
            val result = JsonObject().apply {
                addProperty("path", file.absolutePath)
                addProperty("name", file.name)
                addProperty("exists", exists)
                addProperty("isDirectory", if (exists) file.isDirectory else false)
                addProperty("isFile", if (exists) file.isFile else false)
                addProperty("isHidden", if (exists) file.isHidden else false)
                addProperty("isReadable", file.canRead())
                addProperty("isWritable", file.canWrite())
                addProperty("isExecutable", file.canExecute())
                addProperty("size", if (exists) file.length() else 0)
                addProperty("sizeHuman", if (exists) humanReadableSize(file.length()) else "0 B")
                addProperty("lastModified", if (exists) file.lastModified() else 0)
                addProperty("extension", file.extension)
                addProperty("parent", file.parent)
            }.toString()
            listOf(UIMessagePart.Text(result))
        },
    )

    val all: List<Tool> = listOf(
        readFile, cat, readFiles, writeFile, editFile, multiEditFile, applyBatchEdits,
        createFile, deleteFile, renameFile,
        listFiles, ls, findFiles, glob,
        head, tail, wc, countLines, stat,
    )

    private fun humanReadableSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        for (unit in units) {
            size /= 1024.0
            if (size < 1024.0) return "%.1f %s".format(size, unit)
        }
        return "%.1f PB".format(size)
    }
}
