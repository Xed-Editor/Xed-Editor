package com.rk.ai.agents

object CodexAgent : AiAgent {
    override val name: String = "codex"
    override val displayName: String = "Codex CLI"
    override val cliBinaryName: String = "codex-cli-headless"
    override val shellScriptName: String = "codex-cli"

    override fun buildArgs(extraArgs: List<String>, workingDir: String): List<String> =
        buildList {
            add("exec")
            if (extraArgs.isNotEmpty()) {
                addAll(extraArgs)
            }
        }

    override fun buildEnv(extraEnv: Map<String, String>): Map<String, String> =
        mutableMapOf(
            "EDITOR" to "vim",
            "VISUAL" to "vim",
            "NO_UPDATE_NOTIFIER" to "1",
            "CODEX_QUIET_MODE" to "1",
        ).also { it.putAll(extraEnv) }
}