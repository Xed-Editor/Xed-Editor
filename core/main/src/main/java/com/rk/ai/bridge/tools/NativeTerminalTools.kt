package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService
import java.io.File

class HeadTool : BaseMcpTool() {
    override fun getName(): String = "head"
    override fun getDescription(): String = "NATIVE head - DO NOT use runCommand('head -n ...'). Reads first N lines of a file directly with no shell overhead. Accepts: path, filePath, file, lines, count."
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
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(ideService, filePath)
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
        return textResult(content)
    }
}

class TailTool : BaseMcpTool() {
    override fun getName(): String = "tail"
    override fun getDescription(): String = "NATIVE tail - DO NOT use runCommand('tail -n ...'). Reads last N lines of a file directly with no shell overhead. Accepts: path, filePath, file, lines, count."
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
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(ideService, filePath)
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
        return textResult(content)
    }
}

class WcTool : BaseMcpTool() {
    override fun getName(): String = "wc"
    override fun getDescription(): String = "NATIVE wc - DO NOT use runCommand('wc ...'). Counts lines/words/chars/bytes. No shell overhead. Accepts: path, filePath, file."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Absolute or relative path to the file",
        "filePath" to "Alternative to path",
        "file" to "Alternative to path"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(ideService, filePath)
        if (!file.exists()) throw ToolError.PathOutsideWorkspace("$filePath (file does not exist)")
        var lines = 0L
        var words = 0L
        var chars = 0L
        if (file.isFile) {
            file.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    lines++
                    words += line.split(Regex("\\s+")).count { it.isNotBlank() }
                    chars += line.length + 1
                    line = reader.readLine()
                }
            }
            if (chars > 0) chars--
        }
        return jsonResult(JsonObject().apply {
            addProperty("lines", lines)
            addProperty("words", words)
            addProperty("characters", chars)
            addProperty("bytes", file.length())
            addProperty("path", file.absolutePath)
        })
    }
}

class StatTool : BaseMcpTool() {
    override fun getName(): String = "stat"
    override fun getDescription(): String = "NATIVE stat - DO NOT use runCommand('stat ...' or 'ls -la ...'). Gets file metadata (size, permissions, modified time) instantly. Accepts: path, filePath, file."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Absolute or relative path to the file",
        "filePath" to "Alternative to path",
        "file" to "Alternative to path"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(ideService, filePath)
        val exists = file.exists()
        val size = if (exists) file.length() else 0L
        return jsonResult(JsonObject().apply {
            addProperty("path", file.absolutePath)
            addProperty("name", file.name)
            addProperty("exists", exists)
            addProperty("isDirectory", if (exists) file.isDirectory else false)
            addProperty("isFile", if (exists) file.isFile else false)
            addProperty("isHidden", if (exists) file.isHidden else false)
            addProperty("isReadable", file.canRead())
            addProperty("isWritable", file.canWrite())
            addProperty("isExecutable", file.canExecute())
            addProperty("size", size)
            addProperty("sizeHuman", if (exists) humanReadableSize(size) else "0 B")
            addProperty("lastModified", if (exists) file.lastModified() else 0)
            addProperty("extension", file.extension)
            addProperty("parent", file.parent)
        })
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
    override fun getName(): String = "countLines"
    override fun getDescription(): String = "NATIVE wc -l - DO NOT use runCommand('wc -l ...'). Fast buffered byte-level line counting. Handles files with/without trailing newline."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Absolute or relative path to the file",
        "filePath" to "Alternative to path",
        "file" to "Alternative to path"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(ideService, filePath)
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
        return jsonResult(JsonObject().apply {
            addProperty("lines", lines)
            addProperty("path", file.absolutePath)
        })
    }
}