package com.rk.ai.agents

object GeminiAgent : AiAgent {
    override val name: String = "gemini"
    override val displayName: String = "Gemini CLI"
    override val cliBinaryName: String = "gemini-cli-headless"
    override val shellScriptName: String = "gemini-cli"

    override fun buildArgs(extraArgs: List<String>, workingDir: String): List<String> =
        buildList {
            add("--skip-trust")
            add("--include-directories")
            add(workingDir)
            if (extraArgs.isNotEmpty()) {
                val i = extraArgs.indexOf("--prompt-interactive")
                if (i >= 0 && i + 1 < extraArgs.size) {
                    add("--prompt-interactive")
                    add(extraArgs[i + 1])
                } else {
                    addAll(extraArgs)
                }
            }
        }

    override fun buildEnv(extraEnv: Map<String, String>): Map<String, String> =
        mutableMapOf(
            "GEMINI_TELEMETRY_ENABLED" to "false",
            "GEMINI_TELEMETRY_TARGET" to "local",
            "NO_UPDATE_NOTIFIER" to "1",
            "EDITOR" to "vim",
            "VISUAL" to "vim",
        ).also { it.putAll(extraEnv) }
}
