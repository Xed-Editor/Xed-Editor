package com.rk.ai

import com.google.gson.Gson
import com.rk.ai.agents.AgentTypeRegistry
import com.rk.ai.session.AiSessionManager
import com.rk.settings.Settings
import java.io.File

data class ProjectAiConfig(
    val agent: String? = null,
    val model: String? = null,
    val extraArgs: List<String>? = null,
)

object ProjectConfigLoader {
    private val gson = Gson()

    fun loadForWorkspace(workspacePath: String): ProjectAiConfig? {
        if (!Settings.ai_project_config_enabled) return null
        val root = findProjectRoot(workspacePath) ?: return null
        val configFile = File(root, ".xed/agent.json")
        if (!configFile.exists()) return null
        return try {
            val json = configFile.readText()
            gson.fromJson(json, ProjectAiConfig::class.java)
        } catch (_: Exception) { null }
    }

    fun applyConfig(config: ProjectAiConfig) {
        config.agent?.let { agentType ->
            AgentTypeRegistry.get(agentType.lowercase())?.let {
                AiSessionManager.switchAgent(it.name)
            }
        }
        config.model?.let { model ->
            if (model.isNotBlank()) Settings.ai_model = model
        }
    }

    fun describeConfig(config: ProjectAiConfig): String = buildString {
        append("Project config: ")
        config.agent?.let { append("agent=$it ") }
        config.model?.let { append("model=$it ") }
    }

    private fun findProjectRoot(workspacePath: String): File? {
        var dir = File(workspacePath)
        if (!dir.exists()) return null
        if (dir.isFile) dir = dir.parentFile ?: return null
        // Walk up to find .xed directory
        var current: File? = dir
        while (current != null) {
            if (File(current, ".xed/agent.json").exists()) return current
            if (File(current, ".git").exists()) return current
            current = current.parentFile
        }
        return dir
    }
}
