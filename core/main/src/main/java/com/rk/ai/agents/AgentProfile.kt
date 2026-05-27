package com.rk.ai.agents

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.settings.Settings

data class AgentProfile(
    val name: String,
    val agentType: String = "gemini",
    val extraArgs: String = "",
) {
    fun displayLabel(): String = name
}

object AgentProfileManager {
    private val gson = Gson()

    fun loadProfiles(): List<AgentProfile> {
        val json = Settings.ai_profiles_json
        if (json.isBlank()) return defaultProfiles()
        return try {
            val type = object : TypeToken<List<AgentProfile>>() {}.type
            gson.fromJson(json, type) ?: defaultProfiles()
        } catch (_: Exception) {
            defaultProfiles()
        }
    }

    fun saveProfiles(profiles: List<AgentProfile>) {
        Settings.ai_profiles_json = gson.toJson(profiles)
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
        com.rk.ai.session.AiSessionManager.switchAgent(profile.agentType)
    }

    private fun defaultProfiles(): List<AgentProfile> = listOf(
        AgentProfile(name = "Gemini", agentType = "gemini"),
        AgentProfile(name = "OpenCode", agentType = "opencode"),
    )
}
