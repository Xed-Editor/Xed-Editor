package com.rk.ai.tools

object BuiltinTools {
    fun all(): List<AiTool> =
        FileTools.all() +
            FileOpsTools.all() +
            ShellTools.all() +
            HttpTools.all() +
            StateTools.all() +
            AgentTools.all()
}
