package com.rk.ai.tools

import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The catalogue of every tool the agent can call.
 *
 * Built-in tools are installed first (see [BuiltinTools]); extensions add their own with
 * [registerTool]. Registration is by name: registering a tool whose name already exists replaces the
 * previous one, so an extension can override a built-in. Unregistering an override restores the
 * built-in it shadowed. The list is observable so the settings UI updates when an extension is
 * installed or removed at runtime.
 */
object AiToolRegistry {
    private val lock = Any()
    private val toolsByName = LinkedHashMap<String, AiTool>()
    private var builtinsByName: Map<String, AiTool> = emptyMap()
    private val _tools = MutableStateFlow<List<AiTool>>(emptyList())
    private var builtinsInstalled = false

    /** Every registered tool, in registration order. */
    val tools: StateFlow<List<AiTool>> = _tools.asStateFlow()

    /** A snapshot of every registered tool, safe to iterate while a run is in flight. */
    fun all(): List<AiTool> {
        installBuiltins()
        return _tools.value
    }

    /** The tool with this name, or null when nothing registered it. */
    fun find(name: String): AiTool? = all().firstOrNull { it.name == name }

    /**
     * Registers (or replaces) a tool. Call this from `onLoad` and unregister it from `onDispose` so a
     * removed extension does not leave a dangling capability behind.
     */
    @XedExtensionPoint
    fun registerTool(tool: AiTool) {
        synchronized(lock) {
            toolsByName[tool.name] = tool
            publishLocked()
        }
    }

    /** Registers several tools at once, e.g. a whole tool family from one extension. */
    @XedExtensionPoint
    fun registerTools(vararg tools: AiTool) = tools.forEach(::registerTool)

    /** Removes [tool] if it is still the registered tool under that name. */
    @XedExtensionPoint
    fun unregisterTool(tool: AiTool) {
        synchronized(lock) { removeOrRestoreLocked(tool.name, tool) }
    }

    /** Removes whatever tool is registered under [name]. */
    @XedExtensionPoint
    fun unregisterTool(name: String) {
        synchronized(lock) { removeOrRestoreLocked(name, expected = null) }
    }

    /**
     * Installs the built-in tools exactly once. Called by [com.rk.ai.api.AiExtensions] during feature
     * start-up; the registry also calls it lazily so a tool lookup never misses the built-ins.
     */
    internal fun installBuiltins() {
        val toInstall =
            synchronized(lock) {
                if (builtinsInstalled) return
                builtinsInstalled = true
                BuiltinTools.all().associateBy { it.name }
            }
        synchronized(lock) {
            builtinsByName = toInstall
            toInstall.forEach { (name, tool) ->
                if (!toolsByName.containsKey(name)) toolsByName[name] = tool
            }
            publishLocked()
        }
    }

    /** Removes every tool. Only used by tests, which need a clean registry per case. */
    internal fun resetForTests() {
        synchronized(lock) {
            toolsByName.clear()
            builtinsByName = emptyMap()
            _tools.value = emptyList()
            builtinsInstalled = false
        }
    }

    /**
     * Drops [name] when it still holds [expected] (or anything, when [expected] is null) and puts the
     * built-in back if the removed tool was shadowing one.
     */
    private fun removeOrRestoreLocked(name: String, expected: AiTool?) {
        val current = toolsByName[name] ?: return
        if (expected != null && current !== expected) return
        val builtin = builtinsByName[name]
        if (builtin != null) {
            toolsByName[name] = builtin
        } else {
            toolsByName.remove(name)
        }
        publishLocked()
    }

    private fun publishLocked() {
        _tools.value = toolsByName.values.toList()
    }
}
