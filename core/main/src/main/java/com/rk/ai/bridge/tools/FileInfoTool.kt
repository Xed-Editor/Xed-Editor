package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService
import java.io.File

class GetFileInfoTool : BaseMcpTool() {
    override fun getName(): String = "getFileInfo"
    override fun getDescription(): String =
        "Returns combined metadata AND preview for a file in one call. " +
            "Equivalent to stat + head -n 30 + git log -5, but faster. " +
            "Use this instead of calling stat, head, and gitStatus separately."

    override fun getRequiredParams(): Map<String, String> = emptyMap()
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

        val preview = if (exists && file.isFile && size > 0) {
            file.bufferedReader().use { reader ->
                val lines = mutableListOf<String>()
                var remaining = 30
                while (remaining > 0) {
                    val line = reader.readLine() ?: break
                    lines.add(line)
                    remaining--
                }
                lines.joinToString("\n")
            }
        } else ""

        val md = buildString {
            appendLine("## ${file.name}")
            appendLine()
            appendLine("- **Path:** ${file.absolutePath}")
            appendLine("- **Size:** ${if (exists) humanReadableSize(size) else "N/A"} ($size bytes)")
            appendLine("- **Directory:** ${file.isDirectory}")
            appendLine("- **Modified:** ${if (exists) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(file.lastModified())) else "N/A"}")
            appendLine("- **Readable:** ${file.canRead()}")
            appendLine("- **Writable:** ${file.canWrite()}")
            appendLine("- **Extension:** ${file.extension}")
            if (!exists) appendLine("- **Status:** File does not exist")
            if (file.isDirectory) {
                val children = file.listFiles()?.take(20)?.map { if (it.isDirectory) "${it.name}/" else it.name }?.joinToString(", ") ?: ""
                appendLine("- **Contents:** $children${if ((file.listFiles()?.size ?: 0) > 20) " ..." else ""}")
            }
            if (preview.isNotBlank()) {
                appendLine()
                appendLine("### Preview (first 30 lines)")
                appendLine("```")
                appendLine(preview)
                if (size > 2000) appendLine("... (file truncated)")
                appendLine("```")
            }
        }
        return textResult(md)
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
