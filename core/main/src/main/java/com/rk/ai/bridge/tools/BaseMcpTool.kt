package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.File

abstract class BaseMcpTool : McpTool {

    override suspend fun execute(args: JsonObject, context: McpToolContext): McpToolResult {
        return try {
            validateRequired(args)
            executeValidated(args, context)
        } catch (e: ToolError) {
            McpToolResult.error(e.message, e.code)
        } catch (e: Exception) {
            McpToolResult.error("${e::class.java.simpleName}: ${e.message ?: "internal error"}")
        }
    }

    protected abstract suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult

    // ── Argument extraction ──

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

    protected fun optionalInt(args: JsonObject, name: String, default: Int): Int {
        return args.get(name)?.takeIf { it.isJsonPrimitive }?.asInt ?: default
    }

    protected fun optionalBoolean(args: JsonObject, name: String, default: Boolean = false): Boolean {
        return args.get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default
    }

    protected fun optionalLong(args: JsonObject, name: String, default: Long): Long {
        return args.get(name)?.takeIf { it.isJsonPrimitive }?.asLong ?: default
    }

    // ── Path resolution with security ──

    protected fun safeResolvePath(context: McpToolContext, path: String): File {
        return Security.safeResolve(context.ideService, path) ?: run {
            val file = context.ideService.resolvePath(path)
            if (file != null) {
                val canonical = Security.canonicalize(file)
                val workspace = context.ideService.getPrimaryWorkspacePath()
                if (workspace.isNotBlank() && !Security.isInsideWorkspace(canonical, listOf(workspace))) {
                    throw ToolError.PathOutsideWorkspace(path)
                }
                return canonical
            }
            val workspace = context.ideService.getPrimaryWorkspacePath()
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

    // ── Auto-validation ──

    @Volatile private var cachedRequiredKeys: Set<String>? = null

    private fun requiredKeys(): Set<String> {
        val cached = cachedRequiredKeys
        if (cached != null) return cached
        return requiredParams.keys.toSet().also { cachedRequiredKeys = it }
    }

    private fun validateRequired(args: JsonObject) {
        requiredKeys().forEach { name ->
            val value = args.get(name)
            if (value == null || value.isJsonNull) throw ToolError.MissingParam(name)
            if (value.isJsonPrimitive && value.asString.isBlank()) throw ToolError.MissingParam(name)
        }
    }
}
