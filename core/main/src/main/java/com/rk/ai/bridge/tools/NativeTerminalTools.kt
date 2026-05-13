package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService
import java.io.File
import java.io.RandomAccessFile

class HeadTool : BaseMcpTool() {
    override fun getName(): String = "head"
    override fun getDescription(): String = "Reads first N lines of a file. Native replacement for 'head -n'. Accepts: path, filePath, file, lines."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string",
        "lines" to "number", "count" to "number"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val n = (optionalInt(args, "lines") ?: optionalInt(args, "count") ?: 10).coerceAtMost(10000)
        val file = resolvePathOrThrow(ideService, filePath)
        val content = file.useLines { it.take(n).joinToString("\n") }
        return textResult(content)
    }
}

class TailTool : BaseMcpTool() {
    override fun getName(): String = "tail"
    override fun getDescription(): String = "Reads last N lines of a file. Native replacement for 'tail -n'. Accepts: path, filePath, file, lines."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string",
        "lines" to "number", "count" to "number"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val n = (optionalInt(args, "lines") ?: optionalInt(args, "count") ?: 10).coerceAtMost(10000)
        val file = resolvePathOrThrow(ideService, filePath)
        val content = file.useLines { lines ->
            val deque = ArrayDeque<String>(n)
            lines.forEach { line ->
                if (deque.size == n) deque.removeFirst()
                deque.addLast(line)
            }
            deque.joinToString("\n")
        }
        return textResult(content)
    }
}

class WcTool : BaseMcpTool() {
    override fun getName(): String = "wc"
    override fun getDescription(): String = "Counts lines, words, chars in a file. Native replacement for 'wc'. Accepts: path, filePath, file."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(ideService, filePath)
        if (!file.exists()) throw ToolError.PathOutsideWorkspace("$filePath (file does not exist)")

        var lines = 0L
        var words = 0L
        var chars = 0L

        if (file.isFile) {
            file.useLines { lineSeq ->
                lineSeq.forEach { line ->
                    lines++
                    words += line.split(Regex("\\s+")).count { it.isNotBlank() }
                    chars += line.length + 1
                }
            }
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
    override fun getDescription(): String = "Gets file metadata (size, permissions, modified time). Native replacement for 'stat'/'ls -la'. Accepts: path, filePath, file."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(ideService, filePath)
        val exists = file.exists()

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
            addProperty("size", if (exists) file.length() else 0)
            addProperty("sizeHuman", if (exists) humanReadableSize(file.length()) else "0 B")
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
    override fun getDescription(): String = "Fast line count for a file. Native replacement for 'wc -l'. Uses buffered reading for speed."
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "path" to "string", "filePath" to "string", "file" to "string"
    )
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val filePath = getPathParam(args) ?: throw ToolError.MissingParam("path/filePath/file")
        val file = resolvePathOrThrow(ideService, filePath)

        var lines = 0L
        val buf = ByteArray(8192)
        file.inputStream().use { input ->
            var read = input.read(buf)
            while (read != -1) {
                for (i in 0 until read) {
                    if (buf[i] == 10.toByte()) lines++
                }
                read = input.read(buf)
            }
        }

        return jsonResult(JsonObject().apply {
            addProperty("lines", lines)
            addProperty("path", file.absolutePath)
        })
    }
}
