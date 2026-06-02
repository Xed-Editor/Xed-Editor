package com.rk.ai.agent.hooks

enum class HookEvent {
    BEFORE_FILE_WRITE,
    AFTER_FILE_WRITE,
    BEFORE_FILE_EDIT,
    AFTER_FILE_EDIT,
    BEFORE_COMMAND,
    AFTER_COMMAND,
    BEFORE_TOOL_EXECUTION,
    AFTER_TOOL_EXECUTION,
    ON_GENERATION_START,
    ON_GENERATION_END,
}

data class HookContext(
    val event: HookEvent,
    val toolName: String? = null,
    val filePath: String? = null,
    val newContent: String? = null,
    val command: String? = null,
    val args: Map<String, Any?> = emptyMap(),
    val result: Any? = null,
)

sealed class HookResult {
    data object Allow : HookResult()
    data class Block(val reason: String) : HookResult()
    data class Warn(val message: String) : HookResult()
}

fun interface ToolHook {
    suspend fun evaluate(context: HookContext): HookResult
}

class HookManager {
    private val hooks = mutableMapOf<HookEvent, MutableList<ToolHook>>()

    fun register(event: HookEvent, hook: ToolHook) {
        hooks.getOrPut(event) { mutableListOf() }.add(hook)
    }

    suspend fun evaluate(event: HookEvent, context: HookContext): List<HookResult> {
        val eventHooks = hooks[event] ?: return emptyList()
        return eventHooks.map { it.evaluate(context) }
    }

    suspend fun checkAll(event: HookEvent, context: HookContext): HookResult {
        val results = evaluate(event, context)
        val blocked = results.firstOrNull { it is HookResult.Block }
        if (blocked != null) return blocked
        val warned = results.firstOrNull { it is HookResult.Warn }
        return warned ?: HookResult.Allow
    }

    fun clear() {
        hooks.clear()
    }
}
