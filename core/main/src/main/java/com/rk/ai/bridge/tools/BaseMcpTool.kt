package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import com.rk.ai.bridge.textResult
import java.io.File

abstract class BaseMcpTool : McpTool {

    @Volatile private var cachedRequiredKeys: Set<String>? = null
    @Volatile private var cachedOptionalKeys: Set<String>? = null

    override suspend fun execute(args: JsonObject, context: McpToolContext): McpToolResult {
        val start = System.currentTimeMillis()
        try {
            validateRequired(args)
            val result = executeValidated(args, context)
            return result.copy(durationMs = System.currentTimeMillis() - start)
        } catch (e: ToolError) {
            return McpToolResult.error(e.message, duration = System.currentTimeMillis() - start)
        } catch (e: Exception) {
            return McpToolResult.error("${e::class.java.simpleName}: ${e.message ?: "internal error"}",
                duration = System.currentTimeMillis() - start)
        }
    }

    protected abstract suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult

    override fun getTimeoutMs(): Long = 60_000L

    protected fun requireString(args: JsonObject, name: String, maxLength: Int = 10_485_760): String {
        val value = args.get(name)?.asString.orEmpty().take(maxLength)
        if (value.isBlank()) throw ToolError.MissingParam(name)
        return value
    }

    protected fun requireInt(args: JsonObject, name: String): Int {
        return args.get(name)?.takeIf { it.isJsonPrimitive }?.asInt
            ?: throw ToolError.MissingParam(name)
    }

    protected fun requireBoolean(args: JsonObject, name: String): Boolean {
        return args.get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean
            ?: throw ToolError.MissingParam(name)
    }

    protected fun optionalString(args: JsonObject, name: String, default: String = "", maxLength: Int = 10_485_760): String {
        return args.get(name)?.takeIf { it.isJsonPrimitive }?.asString?.take(maxLength) ?: default
    }

    protected fun optionalInt(args: JsonObject, name: String, default: Int? = null): Int? {
        val value = args.get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asInt
        return value ?: default
    }

    protected fun optionalPositiveInt(args: JsonObject, name: String, default: Int? = null): Int? {
        val value = args.get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asInt
        return if (value != null && value > 0) value else default
    }

    protected fun optionalBoolean(args: JsonObject, name: String, default: Boolean = false): Boolean {
        return args.get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default
    }

    protected fun optionalLong(args: JsonObject, name: String, default: Long = 0L): Long {
        return args.get(name)?.takeIf { it.isJsonPrimitive }?.asLong ?: default
    }

    protected fun getPathParam(args: JsonObject): String? {
        return args.get("path")?.asString
            ?: args.get("filePath")?.asString
            ?: args.get("file")?.asString
            ?: args.get("name")?.asString
    }

    protected fun getContentParam(args: JsonObject): String? {
        return args.get("content")?.asString
            ?: args.get("text")?.asString
            ?: args.get("newContent")?.asString
    }

    protected fun getQueryParam(args: JsonObject): String? {
        return args.get("query")?.asString
            ?: args.get("pattern")?.asString
            ?: args.get("search")?.asString
            ?: args.get("text")?.asString
    }

    protected fun resolvePathOrThrow(context: McpToolContext, path: String): File {
        val ideService = context.ideService
        return ideService.resolvePath(path) ?: run {
            val workspace = ideService.getPrimaryWorkspacePath()
            if (workspace.isNotBlank()) {
                val root = File(workspace)
                val name = path.substringAfterLast("/")
                val suggestions = root.walkTopDown()
                    .maxDepth(3)
                    .filter { it.name.contains(name, ignoreCase = true) }
                    .take(3)
                    .map { it.absolutePath }
                    .toList()
                if (suggestions.isNotEmpty()) {
                    throw ToolError.PathOutsideWorkspace("$path not found. Did you mean:\n${suggestions.joinToString("\n")}")
                }
            }
            throw ToolError.PathOutsideWorkspace(path)
        }
    }

    private fun requiredKeys(): Set<String> {
        val cached = cachedRequiredKeys
        if (cached != null) return cached
        val keys = getRequiredParams().keys.toSet()
        cachedRequiredKeys = keys
        return keys
    }

    private fun validateRequired(args: JsonObject) {
        requiredKeys().forEach { name ->
            val value = args.get(name)
            if (value == null || value.isJsonNull) throw ToolError.MissingParam(name)
            if (value.isJsonPrimitive && value.asString.isBlank()) throw ToolError.MissingParam(name)
        }
    }
}
