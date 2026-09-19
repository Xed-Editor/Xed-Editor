package com.rk.ai.tools

/**
 * The complete built-in tool set.
 *
 * It is assembled here rather than inside the registries so the registry stays free of tool
 * knowledge: [AiToolRegistry.installBuiltins] calls [all] exactly once, and extensions register on
 * top of the result.
 */
object BuiltinTools {
    fun all(): List<AiTool> =
        FileTools.all() +
            FileOpsTools.all() +
            ShellTools.all() +
            HttpTools.all() +
            StateTools.all() +
            AgentTools.all()
}
