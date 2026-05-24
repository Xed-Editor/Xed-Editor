package com.rk.ai.agents

object OpenCodeAgent : AiAgent {
    override val name: String = "opencode"
    override val displayName: String = "OpenCode"
    override val cliBinaryName: String = "opencode-cli-headless"
    override val shellScriptName: String = "opencode-cli"
    override val modelFlagName: String = "-m"
    override val defaultModel: String = ""

    override fun buildArgs(extraArgs: List<String>, workingDir: String, model: String?): List<String> =
        buildList {
            if (!model.isNullOrBlank()) {
                add(modelFlagName)
                add(model)
            }
            if (extraArgs.isEmpty()) return@buildList
            val i = extraArgs.indexOf("--prompt-interactive")
            if (i >= 0 && i + 1 < extraArgs.size) {
                add("--prompt")
                add(extraArgs[i + 1])
            } else {
                addAll(extraArgs)
            }
        }

    override fun buildEnv(extraEnv: Map<String, String>): Map<String, String> =
        mutableMapOf(
            "EDITOR" to "vim",
            "VISUAL" to "vim",
            "NO_UPDATE_NOTIFIER" to "1",
        ).also { it.putAll(extraEnv) }
}
