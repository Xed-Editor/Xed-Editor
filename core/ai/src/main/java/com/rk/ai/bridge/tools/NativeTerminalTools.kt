package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.File

class HeadTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "head"
    override fun getDescription(): String = "Reads first N lines of a file. Accepts: path, filePath, file, lines, count."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string",
        "lines" to "number", "count" to "number"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Absolute or relative path to the file",
        "filePath" to "Alternative to path",
        "file" to "Alternative to path",
        "lines" to "Number of lines to read from the top (default: 10, max: 10000)",
        "count" to "Alias for lines"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(context, filePath)
        val n = (optionalInt(args, "lines") ?: optionalInt(args, "count") ?: 10).coerceAtMost(10000)
        val content = file.bufferedReader().use { reader ->
            val lines = mutableListOf<String>()
            var remaining = n
            while (remaining > 0) {
                val line = reader.readLine() ?: break
                lines.add(line)
                remaining--
            }
            lines.joinToString("\n")
        }
        return McpToolResult.success(content)
    }
}

class TailTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "tail"
    override fun getDescription(): String = "Reads last N lines of a file. Accepts: path, filePath, file, lines, count."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string",
        "lines" to "number", "count" to "number"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Absolute or relative path to the file",
        "filePath" to "Alternative to path",
        "file" to "Alternative to path",
        "lines" to "Number of lines to read from the bottom (default: 10, max: 10000)",
        "count" to "Alias for lines"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(context, filePath)
        val n = (optionalInt(args, "lines") ?: optionalInt(args, "count") ?: 10).coerceAtMost(10000)
        val content = file.bufferedReader().use { reader ->
            val ring = ArrayDeque<String>(n)
            var line = reader.readLine()
            while (line != null) {
                if (ring.size == n) ring.removeFirst()
                ring.addLast(line)
                line = reader.readLine()
            }
            ring.joinToString("\n")
        }
        return McpToolResult.success(content)
    }
}

class WcTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "wc"
    override fun getDescription(): String = "Counts lines/words/chars/bytes. Accepts: path, filePath, file."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Absolute or relative path to the file",
        "filePath" to "Alternative to path",
        "file" to "Alternative to path"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(context, filePath)
        if (!file.exists()) throw ToolError.FileNotFound(filePath)

        var lines = 0L
        var words = 0L
        var chars = 0L

        if (file.isFile) {
            val text = file.readText()
            if (text.isNotEmpty()) {
                lines = text.count { it == '\n' }.toLong()
                if (text.last() != '\n') lines++
                words = text.split(Regex("\\s+")).count { it.isNotBlank() }.toLong()
                chars = text.length.toLong()
            }
        }

        return McpToolResult.success(JsonObject().apply {
            addProperty("lines", lines)
            addProperty("words", words)
            addProperty("characters", chars)
            addProperty("bytes", file.length())
            addProperty("path", file.absolutePath)
        }.toString())
    }
}

class StatTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "stat"
    override fun getDescription(): String = "Gets file metadata (size, permissions, modified time). Accepts: path, filePath, file."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Absolute or relative path to the file",
        "filePath" to "Alternative to path",
        "file" to "Alternative to path"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(context, filePath)
        val exists = file.exists()

        return McpToolResult.success(JsonObject().apply {
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
        }.toString())
    }

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

class CountLinesTool : BaseMcpTool() {
    override fun getCategory(): String = "File Operations"
    override fun getName(): String = "countLines"
    override fun getDescription(): String = "Fast buffered byte-level line counting. Accepts: path, filePath, file."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Absolute or relative path to the file",
        "filePath" to "Alternative to path",
        "file" to "Alternative to path"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(context, filePath)

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

        return McpToolResult.success(JsonObject().apply {
            addProperty("lines", lines)
            addProperty("path", file.absolutePath)
        }.toString())
    }
}
