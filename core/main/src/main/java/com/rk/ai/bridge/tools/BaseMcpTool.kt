package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService
import java.io.File

abstract class BaseMcpTool : McpTool {

    @Volatile private var cachedRequiredKeys: Set<String>? = null
    @Volatile private var cachedOptionalKeys: Set<String>? = null

    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        validateRequired(args)
        return executeValidated(args, ideService)
    }

    protected abstract suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject

    override fun getTimeoutMs(): Long = 60_000L

    // ── Argument extraction helpers ──

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

    protected fun optionalInt(args: JsonObject, name: String, default: Int = 0): Int? {
        val value = args.get(name)?.takeIf { it.isJsonPrimitive }?.asInt
        return if (value != null && value > 0) value else null
    }

    protected fun optionalBoolean(args: JsonObject, name: String, default: Boolean = false): Boolean {
        return args.get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default
    }

    protected fun resolvePathOrThrow(ideService: IdeService, path: String): File {
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

    // ── Auto-validation from schema (cached keys) ──

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
