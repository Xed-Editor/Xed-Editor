package com.rk.ai.tools

import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The catalogue of every tool the agent can call; registering a name replaces the previous entry. */
object AiToolRegistry {
    private val lock = Any()
    private val toolsByName = LinkedHashMap<String, AiTool>()
    private var builtinsByName: Map<String, AiTool> = emptyMap()
    private val _tools = MutableStateFlow<List<AiTool>>(emptyList())
    private var builtinsInstalled = false

    val tools: StateFlow<List<AiTool>> = _tools.asStateFlow()

    /** A snapshot, safe to iterate while a run is in flight. */
    fun all(): List<AiTool> {
        installBuiltins()
        return _tools.value
    }

    fun find(name: String): AiTool? = all().firstOrNull { it.name == name }

    @XedExtensionPoint
    fun registerTool(tool: AiTool) {
        synchronized(lock) {
            toolsByName[tool.name] = tool
            publishLocked()
        }
    }

    /** Registers several tools at once. */
    @XedExtensionPoint
    fun registerTools(vararg tools: AiTool) = tools.forEach(::registerTool)

    /** Removes [tool] if it is still the registered tool under that name. */
    @XedExtensionPoint
    fun unregisterTool(tool: AiTool) {
        synchronized(lock) { removeOrRestoreLocked(tool.name, tool) }
    }

    @XedExtensionPoint
    fun unregisterTool(name: String) {
        synchronized(lock) { removeOrRestoreLocked(name, expected = null) }
    }

    /** Installs the built-in tools exactly once, lazily if needed. */
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

    internal fun resetForTests() {
        synchronized(lock) {
            toolsByName.clear()
            builtinsByName = emptyMap()
            _tools.value = emptyList()
            builtinsInstalled = false
        }
    }

    /** Drops [name] and puts back the built-in it was shadowing, if any. */
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
