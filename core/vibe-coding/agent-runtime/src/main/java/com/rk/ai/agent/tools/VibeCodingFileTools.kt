@file:OptIn(ExperimentalUuidApi::class)

package com.rk.ai.agent.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService
import java.io.File

class VibeCodingFileTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(
        readFile, cat, readFiles, writeFile, editFile, applyBatchEdits,
        createFile, deleteFile, renameFile,
        listFiles, ls, findFiles, glob,
        head, tail, wc, countLines, stat,
    )

    private val readFile = Tool(
        name = "readFile",
        description = "Read the contents of a file. Supports startLine/endLine (1-indexed, inclusive). Content truncated at 250KB.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("path", "Absolute or workspace-relative path to the file")
                    putJsonObject("startLine") { put("type", "integer"); put("description", "First line to read (1-indexed)") }
                    putJsonObject("endLine") { put("type", "integer"); put("description", "Last line to read (inclusive)") }
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

    private val cat = Tool(
        name = "cat",
        description = "Alias for readFile. Same as readFile. Accepts: path, filePath, file.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("path", "Absolute or relative path to the file")
                    put("filePath", "Alternative to path")
                    put("file", "Alternative to path")
                    putJsonObject("startLine") { put("type", "integer") }
                    putJsonObject("endLine") { put("type", "integer") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString
                ?: obj["filePath"]?.asJsonPrimitive?.asString
                ?: obj["file"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file argument"))
            val startLine = obj["startLine"]?.asJsonPrimitive?.asInt
            val endLine = obj["endLine"]?.asJsonPrimitive?.asInt
            val content = ideService.getFileContent(path, startLine, endLine)
            if (content != null) listOf(UIMessagePart.Text(content))
            else listOf(UIMessagePart.Text("File not found: $path"))
        },
    )

    private val readFiles = Tool(
        name = "readFiles",
        description = "RECOMMENDED: Reads multiple files at once. Input can be comma-separated paths or JSON array of path strings.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("filePaths", "Comma-separated list of paths or JSON array of path strings")
                },
                required = listOf("filePaths"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val raw = obj["filePaths"]?.asJsonPrimitive?.asString
            val paths = if (raw != null) {
                raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                val arr = obj["filePaths"]?.asJsonArray
                if (arr != null) arr.map { it.asString }
                else emptyList()
            }
            if (paths.isEmpty()) return@Tool listOf(UIMessagePart.Text("Missing filePaths argument"))
            val results = paths.map { path ->
                val content = ideService.getFileContent(path, null, null)
                if (content != null) "--- $path ---\n$content"
                else "--- $path ---\n(FILE NOT FOUND)"
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
                    put("path", "Absolute path to the file")
                    put("content", "The full content to write")
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
        description = "Surgically replace text in a file using exact string matching. PREFERRED for targeted changes. Supports dryRun, partialMatch, and replaceAll.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("filePath", "Absolute path to the file")
                    put("oldString", "The exact text to find and replace")
                    put("newString", "The replacement text")
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
            val content = ideService.getFileContent(filePath, null, null) ?: return@Tool listOf(UIMessagePart.Text("File not found: $filePath"))

            val matches = if (replaceAll) {
                if (content.contains(oldString)) 1 else 0
            } else {
                var idx = 0
                var count = 0
                while (true) {
                    idx = content.indexOf(oldString, idx)
                    if (idx == -1) break
                    count++
                    idx += oldString.length
                }
                count
            }

            if (replaceAll && content.contains(oldString)) {
                if (dryRun) return@Tool listOf(UIMessagePart.Text("[dry-run] Would edit $filePath (replaceAll: ${oldString.length} chars -> ${newString.length} chars)"))
                val result = content.replace(oldString, newString)
                ideService.writeFile(File(filePath), result)
                listOf(UIMessagePart.Text("Edited $filePath (replaced all occurrences)"))
            } else if (matches == 1) {
                if (dryRun) return@Tool listOf(UIMessagePart.Text("[dry-run] Would edit $filePath (${oldString.length} chars -> ${newString.length} chars)"))
                val result = content.replace(oldString, newString)
                ideService.writeFile(File(filePath), result)
                listOf(UIMessagePart.Text("Edited $filePath"))
            } else if (matches > 1) {
                listOf(UIMessagePart.Text("Found $matches matches in $filePath. Provide more surrounding context in oldString to identify the correct match, or use replaceAll=true."))
            } else if (partialMatch) {
                val idx = content.indexOf(oldString.take(20))
                if (idx != -1) {
                    if (dryRun) return@Tool listOf(UIMessagePart.Text("[dry-run] Would edit $filePath via partial match"))
                    val result = content.replace(oldString, newString)
                    ideService.writeFile(File(filePath), result)
                    listOf(UIMessagePart.Text("Edited $filePath (partial match)"))
                } else {
                    listOf(UIMessagePart.Text("Could not find the specified text in $filePath (tried partial match too)"))
                }
            } else {
                listOf(UIMessagePart.Text("Could not find the specified text in $filePath"))
            }
        },
    )

    private val applyBatchEdits = Tool(
        name = "applyBatchEdits",
        description = "RECOMMENDED: Applies multiple file changes at once. ALWAYS use this for cross-file refactorings to ensure consistency and minimize turns. Takes a JSON object where keys are absolute file paths and values are new content.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("edits", "JSON object mapping file paths to their new content: {\"path/to/file.kt\": \"new content...\"}")
                },
                required = listOf("edits"),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val editsObj = obj.getAsJsonObject("edits") ?: return@Tool listOf(UIMessagePart.Text("Missing edits argument (must be a JSON object)"))
            val edits = mutableMapOf<String, String>()
            editsObj.entrySet().forEach { entry ->
                val path = entry.key
                val file = ideService.resolvePath(path)
                edits[file?.absolutePath ?: path] = entry.value.asString
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
                    put("filePath", "Absolute path for the new file")
                    put("content", "Initial file content (optional)")
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
                    put("filePath", "Absolute path of the file to delete")
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
                    put("sourcePath", "Current path of the file or directory")
                    put("destPath", "New path for the file or directory")
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
                    put("path", "Directory path to list")
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
                    put("path", "Directory path to list")
                    put("directoryPath", "Alternative to path")
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
                    put("query", "File name or glob pattern to search for (e.g. *.kt, **/*.java)")
                    put("pattern", "Alternative to query")
                    putJsonObject("limit") { put("type", "integer"); put("description", "Maximum results (default: 100)") }
                    put("path", "Directory to search in (default: workspace root)")
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
            val path = obj["path"]?.asJsonPrimitive?.asString
            val results = ideService.findFiles(query, limit, path)
            if (results.size() > 0) {
                val text = results.joinToString("\n") { it.asString }
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
                    put("query", "File name or glob pattern to search for (e.g. *.kt, **/*.java)")
                    put("pattern", "Alternative to query")
                    putJsonObject("limit") { put("type", "integer"); put("description", "Maximum results (default: 100)") }
                    put("path", "Directory to search in (default: workspace root)")
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
            val path = obj["path"]?.asJsonPrimitive?.asString
            val results = ideService.findFiles(query, limit, path)
            if (results.size() > 0) {
                val text = results.joinToString("\n") { it.asString }
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
                    put("path", "Absolute or relative path to the file")
                    put("filePath", "Alternative to path")
                    put("file", "Alternative to path")
                    putJsonObject("lines") { put("type", "integer"); put("description", "Number of lines to read from the top (default: 10, max: 10000)") }
                    putJsonObject("count") { put("type", "integer"); put("description", "Alias for lines") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString
                ?: obj["filePath"]?.asJsonPrimitive?.asString
                ?: obj["file"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file"))
            val n = (obj["lines"]?.asJsonPrimitive?.asInt ?: obj["count"]?.asJsonPrimitive?.asInt ?: 10).coerceIn(1, 10000)
            val content = ideService.getFileContent(path, 1, n)
            if (content != null) listOf(UIMessagePart.Text(content))
            else listOf(UIMessagePart.Text("File not found: $path"))
        },
    )

    private val tail = Tool(
        name = "tail",
        description = "Reads last N lines of a file. Accepts: path, filePath, file, lines, count.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("path", "Absolute or relative path to the file")
                    put("filePath", "Alternative to path")
                    put("file", "Alternative to path")
                    putJsonObject("lines") { put("type", "integer"); put("description", "Number of lines to read from the bottom (default: 10, max: 10000)") }
                    putJsonObject("count") { put("type", "integer"); put("description", "Alias for lines") }
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString
                ?: obj["filePath"]?.asJsonPrimitive?.asString
                ?: obj["file"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file"))
            val n = (obj["lines"]?.asJsonPrimitive?.asInt ?: obj["count"]?.asJsonPrimitive?.asInt ?: 10).coerceIn(1, 10000)
            val fullContent = ideService.getFileContent(path, null, null)
            if (fullContent == null) return@Tool listOf(UIMessagePart.Text("File not found: $path"))
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
                    put("path", "Absolute or relative path to the file")
                    put("filePath", "Alternative to path")
                    put("file", "Alternative to path")
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString
                ?: obj["filePath"]?.asJsonPrimitive?.asString
                ?: obj["file"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file"))
            val file = ideService.resolvePath(path)
            if (file == null || !file.exists()) return@Tool listOf(UIMessagePart.Text("File not found: $path"))
            val text = file.readText()
            val lines = if (text.isEmpty()) 0L else {
                val count = text.count { it == '\n' }.toLong()
                if (text.last() != '\n') count + 1 else count
            }
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }.toLong()
            val chars = text.length.toLong()
            val bytes = file.length()
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
                    put("path", "Absolute or relative path to the file")
                    put("filePath", "Alternative to path")
                    put("file", "Alternative to path")
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString
                ?: obj["filePath"]?.asJsonPrimitive?.asString
                ?: obj["file"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file"))
            val file = ideService.resolvePath(path)
            if (file == null || !file.exists()) return@Tool listOf(UIMessagePart.Text("File not found: $path"))
            var lines = 0L
            var lastByte = -1
            val buf = ByteArray(8192)
            file.inputStream().use { input ->
                var read = input.read(buf)
                while (read != -1) {
                    for (i in 0 until read) {
                        if (buf[i] == 10.toByte()) lines++
                    }
                    if (read > 0) lastByte = buf[read - 1].toInt()
                    read = input.read(buf)
                }
            }
            if (lastByte != -1 && lastByte != 10) lines++
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
                    put("path", "Absolute or relative path to the file")
                    put("filePath", "Alternative to path")
                    put("file", "Alternative to path")
                },
                required = emptyList(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString
                ?: obj["filePath"]?.asJsonPrimitive?.asString
                ?: obj["file"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing path/filePath/file"))
            val file = ideService.resolvePath(path)
            if (file == null) return@Tool listOf(UIMessagePart.Text("Path not found: $path"))
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
