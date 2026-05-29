package com.rk.ai.agents

import com.rk.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AgentProfile(
    val name: String,
    val agentType: String = "gemini",
    val extraArgs: String = "",
) {
    fun displayLabel(): String = name
}

object AgentProfileManager {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun loadProfiles(): List<AgentProfile> {
        val jsonText = Settings.ai_profiles_json
        if (jsonText.isBlank()) return defaultProfiles()
        return try {
            jsonParser.decodeFromString<List<AgentProfile>>(jsonText)
        } catch (_: Exception) {
            defaultProfiles()
        }
    }

    fun saveProfiles(profiles: List<AgentProfile>) {
        Settings.ai_profiles_json = jsonParser.encodeToString(profiles)
    }

    fun addProfile(profile: AgentProfile) {
        val profiles = loadProfiles().toMutableList()
        profiles.removeAll { it.name == profile.name }
        profiles.add(profile)
        saveProfiles(profiles)
    }

    fun deleteProfile(name: String) {
        val profiles = loadProfiles().toMutableList()
        profiles.removeAll { it.name == name }
        saveProfiles(profiles)
    }

    fun applyProfile(profile: AgentProfile) {
        Settings.ai_agent = profile.agentType
        Settings.ai_agent_extra_args = profile.extraArgs
        com.rk.ai.session.AiSessionManager.switchAgent(profile.agentType)
    }

    private fun defaultProfiles(): List<AgentProfile> = listOf(
        AgentProfile(name = "Gemini", agentType = "gemini"),
        AgentProfile(name = "OpenCode", agentType = "opencode"),
        AgentProfile(name = "Antigravity", agentType = "antigravity"),
        AgentProfile(name = "Codex", agentType = "codex"),
    )
}
