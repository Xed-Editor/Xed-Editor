package com.rk.ai.agents

interface AiAgent {
    val name: String
    val displayName: String
    val cliBinaryName: String
    val shellScriptName: String
    val modelFlagName: String
    val defaultModel: String
    fun buildArgs(extraArgs: List<String>, workingDir: String, model: String? = null): List<String>
    fun buildEnv(extraEnv: Map<String, String>): Map<String, String>
}
