package com.rk.ai.coding.tools

import com.google.gson.JsonObject

enum class ToolPermissionLevel {
    AutoAllow,
    Ask,
    Deny,
}

data class ToolPreview(
    val title: String,
    val filePath: String? = null,
    val oldContent: String? = null,
    val newContent: String? = null,
    val unifiedDiff: String? = null,
)

data class ToolApprovalRequest(
    val sessionId: String,
    val toolCallId: String,
    val toolName: String,
    val input: String,
    val permission: ToolPermissionLevel,
    val preview: ToolPreview? = null,
)

class ToolPermissionManager(
    overrides: Map<String, ToolPermissionLevel> = emptyMap(),
) {
    private val permissions = defaultPermissions().toMutableMap().apply { putAll(overrides) }

    fun getPermission(toolName: String): ToolPermissionLevel =
        permissions[toolName] ?: ToolPermissionLevel.Ask

    fun setPermission(toolName: String, level: ToolPermissionLevel) {
        permissions[toolName] = level
    }

    fun needsApproval(toolName: String): Boolean = getPermission(toolName) == ToolPermissionLevel.Ask

    suspend fun buildPreview(
        sessionId: String,
        toolCallId: String,
        toolName: String,
        input: JsonObject,
        rawInput: String,
        context: NativeToolContext,
    ): ToolApprovalRequest {
        return ToolApprovalRequest(
            sessionId = sessionId,
            toolCallId = toolCallId,
            toolName = toolName,
            input = rawInput,
            permission = getPermission(toolName),
            preview = when (toolName) {
                "writeFile" -> writeFilePreview(input, context)
                "deleteFile" -> deleteFilePreview(input, context)
                "createFile" -> createFilePreview(input)
                else -> ToolPreview(title = "Approve tool call: $toolName")
            },
        )
    }

    private suspend fun writeFilePreview(input: JsonObject, context: NativeToolContext): ToolPreview {
        val path = input.optionalStringParam("filePath", "path", "file")
        val newContent = input.optionalStringParam("content", "newContent")
        val file = path.takeIf { it.isNotBlank() }?.let { context.fileOps.resolvePath(it) }
        val oldContent = file?.let { context.fileOps.getFileContent(it.absolutePath) }.orEmpty()
        return ToolPreview(
            title = "Review file update",
            filePath = file?.absolutePath ?: path,
            oldContent = oldContent,
            newContent = newContent,
            unifiedDiff = unifiedDiff(file?.absolutePath ?: path, oldContent, newContent),
        )
    }

    private suspend fun deleteFilePreview(input: JsonObject, context: NativeToolContext): ToolPreview {
        val path = input.optionalStringParam("filePath", "path")
        val file = path.takeIf { it.isNotBlank() }?.let { context.fileOps.resolvePath(it) }
        val oldContent = file?.let { context.fileOps.getFileContent(it.absolutePath) }.orEmpty()
        return ToolPreview(
            title = "Review file deletion",
            filePath = file?.absolutePath ?: path,
            oldContent = oldContent,
            newContent = "",
            unifiedDiff = unifiedDiff(file?.absolutePath ?: path, oldContent, ""),
        )
    }

    private fun createFilePreview(input: JsonObject): ToolPreview {
        val path = input.optionalStringParam("filePath", "path")
        val content = input.optionalStringParam("content")
        return ToolPreview(
            title = "Review file creation",
            filePath = path,
            oldContent = "",
            newContent = content,
            unifiedDiff = unifiedDiff(path, "", content),
        )
    }

    private fun defaultPermissions(): Map<String, ToolPermissionLevel> = mapOf(
        "readFile" to ToolPermissionLevel.AutoAllow,
        "searchWorkspace" to ToolPermissionLevel.AutoAllow,
        "listFiles" to ToolPermissionLevel.AutoAllow,
        "gitStatus" to ToolPermissionLevel.AutoAllow,
        "gitDiff" to ToolPermissionLevel.AutoAllow,
        "terminalRead" to ToolPermissionLevel.AutoAllow,
        "writeFile" to ToolPermissionLevel.Ask,
        "createFile" to ToolPermissionLevel.Ask,
        "deleteFile" to ToolPermissionLevel.Ask,
        "terminalExecute" to ToolPermissionLevel.Ask,
    )
}

fun unifiedDiff(path: String, oldContent: String, newContent: String): String {
    if (oldContent == newContent) return "No changes."
    val oldLines = oldContent.lines()
    val newLines = newContent.lines()
    var prefix = 0
    while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) {
        prefix++
    }
    var suffix = 0
    while (
        suffix < oldLines.size - prefix &&
            suffix < newLines.size - prefix &&
            oldLines[oldLines.lastIndex - suffix] == newLines[newLines.lastIndex - suffix]
    ) {
        suffix++
    }

    val oldChanged = oldLines.subList(prefix, oldLines.size - suffix)
    val newChanged = newLines.subList(prefix, newLines.size - suffix)
    val contextBefore = oldLines.subList((prefix - 3).coerceAtLeast(0), prefix)
    val contextAfterStart = oldLines.size - suffix
    val contextAfter = oldLines.subList(contextAfterStart, (contextAfterStart + 3).coerceAtMost(oldLines.size))

    return buildString {
        appendLine("--- a/$path")
        appendLine("+++ b/$path")
        appendLine("@@ -${prefix + 1},${oldChanged.size} +${prefix + 1},${newChanged.size} @@")
        contextBefore.forEach { appendLine(" $it") }
        oldChanged.forEach { appendLine("-$it") }
        newChanged.forEach { appendLine("+$it") }
        contextAfter.forEach { appendLine(" $it") }
    }.trimEnd()
}
