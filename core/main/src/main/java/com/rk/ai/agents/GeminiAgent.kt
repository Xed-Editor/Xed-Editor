package com.rk.ai.agents

import com.rk.file.FileObject
import com.rk.file.FileWrapper

object GeminiAgent : AiAgent {
    override val name: String = "gemini"
    override val displayName: String = "Gemini CLI"
    override val cliBinaryName: String = "gemini-cli-headless"
    override val shellScriptName: String = "gemini-cli"
    override val modelFlagName: String = "--model"
    override val defaultModel: String = "gemini-2.5-flash"

    override fun buildArgs(extraArgs: List<String>, workingDir: String, model: String?): List<String> =
        buildList {
            add("--skip-trust")
            add("--include-directories")
            add(workingDir)
            if (!model.isNullOrBlank()) {
                add(modelFlagName)
                add(model)
            }
            if (extraArgs.isNotEmpty()) {
                addAll(extraArgs)
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

    override fun modelArg(model: String?): List<String> =
        if (!model.isNullOrBlank()) listOf(modelFlagName, model) else emptyList()

    override suspend fun workingDirFor(file: FileObject, projectRoot: FileObject?): String =
        projectRoot?.getAbsolutePath()
            ?: (file.getParentFile() as? FileWrapper)?.getAbsolutePath()
            ?: file.getAbsolutePath()
}
