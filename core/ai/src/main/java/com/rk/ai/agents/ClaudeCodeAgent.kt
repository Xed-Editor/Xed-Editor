package com.rk.ai.agents

object ClaudeCodeAgent : AiAgent {
    override val name: String = "claude"
    override val displayName: String = "Claude Code"
    override val cliBinaryName: String = "claude-cli-headless"
    override val shellScriptName: String = "claude-cli"

    override fun buildArgs(extraArgs: List<String>, workingDir: String): List<String> =
        buildList {
            if (extraArgs.isNotEmpty()) {
                val i = extraArgs.indexOf("--prompt-interactive")
                if (i >= 0 && i + 1 < extraArgs.size) {
                    add("-p")
                    add(extraArgs[i + 1])
                } else {
                    addAll(extraArgs)
                }
            }
        }

    override fun buildEnv(extraEnv: Map<String, String>): Map<String, String> =
        mutableMapOf(
            "EDITOR" to "vim",
            "VISUAL" to "vim",
            "NO_UPDATE_NOTIFIER" to "1",
            "CLAUDE_CODE_QUIET_MODE" to "1",
            "CLAUDE_CODE_AGENT_ALLOW_DANGEROUS" to "1",
        ).also { it.putAll(extraEnv) }
}
