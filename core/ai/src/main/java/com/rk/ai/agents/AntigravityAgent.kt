package com.rk.ai.agents

object AntigravityAgent : AiAgent {
    override val name: String = "antigravity"
    override val displayName: String = "Antigravity CLI"
    override val cliBinaryName: String = "antigravity-cli-headless"
    override val shellScriptName: String = "antigravity-cli"

    override fun buildArgs(extraArgs: List<String>, workingDir: String): List<String> =
        buildList {
            if (extraArgs.isNotEmpty()) {
                val i = extraArgs.indexOf("--prompt-interactive")
                if (i >= 0 && i + 1 < extraArgs.size) {
                    add(extraArgs[i + 1])
                } else {
                    addAll(extraArgs)
                }
                add("--print")
                add("--dangerously-skip-permissions")
                add("--print-timeout")
                add("60s")
            }
        }

    override fun buildEnv(extraEnv: Map<String, String>): Map<String, String> =
        mutableMapOf(
            "EDITOR" to "vim",
            "VISUAL" to "vim",
            "NO_UPDATE_NOTIFIER" to "1",
            "SSH_CONNECTION" to "127.0.0.1 0 127.0.0.1 0",
        ).also { it.putAll(extraEnv) }
}
