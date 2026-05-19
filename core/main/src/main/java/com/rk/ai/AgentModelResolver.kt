package com.rk.ai

import android.util.Log
import com.rk.ai.agents.AiAgent
import com.rk.settings.Settings
import com.rk.xededitor.BuildConfig

/**
 * Prevents cross-agent model mismatches from breaking startup.
 * OpenCode expects "provider/model"; Gemini accepts its own model IDs.
 */
fun resolveModelForAgent(agent: AiAgent, configuredModel: String?): String? {
    val model = configuredModel?.trim().orEmpty()
    if (model.isBlank()) return null

    return when (agent.name) {
        "opencode" -> {
            if (model.contains('/')) model else {
                if (BuildConfig.DEBUG) {
                    Log.w(
                        "AgentModelResolver",
                        "Ignoring invalid OpenCode model '$model' (expected provider/model)."
                    )
                }
                null
            }
        }
        else -> model
    }
}

private fun readModelSlot(agentName: String): String = when (agentName) {
    "gemini" -> Settings.ai_model_gemini
    "opencode" -> Settings.ai_model_opencode
    else -> Settings.ai_model
}.trim()

private fun writeModelSlot(agentName: String, model: String) {
    when (agentName) {
        "gemini" -> Settings.ai_model_gemini = model
        "opencode" -> Settings.ai_model_opencode = model
        else -> Settings.ai_model = model
    }
}

private fun migrateLegacyModelIfNeeded(agent: AiAgent): String {
    val legacy = Settings.ai_model.trim()
    if (legacy.isBlank() || Settings.ai_agent != agent.name) return ""
    val resolved = resolveModelForAgent(agent, legacy).orEmpty()
    if (resolved != legacy && BuildConfig.DEBUG) {
        Log.w(
            "AgentModelResolver",
            "Dropping incompatible legacy model '$legacy' for ${agent.name}."
        )
    }
    writeModelSlot(agent.name, resolved)
    return resolved
}

fun configuredModelForAgent(agent: AiAgent): String {
    val stored = readModelSlot(agent.name)
    return if (stored.isNotBlank()) stored else migrateLegacyModelIfNeeded(agent)
}

fun resolvedConfiguredModelForAgent(agent: AiAgent): String? {
    val configured = configuredModelForAgent(agent)
    val resolved = resolveModelForAgent(agent, configured).orEmpty()
    if (resolved != configured) {
        writeModelSlot(agent.name, resolved)
    }
    if (Settings.ai_agent == agent.name && Settings.ai_model != resolved) {
        Settings.ai_model = resolved
    }
    return resolved.ifBlank { null }
}

fun resolvedStoredModelForAgent(agent: AiAgent): String? {
    val stored = readModelSlot(agent.name)
    val resolved = resolveModelForAgent(agent, stored).orEmpty()
    if (resolved != stored) {
        writeModelSlot(agent.name, resolved)
    }
    return resolved.ifBlank { null }
}

fun setConfiguredModelForAgent(
    agent: AiAgent,
    model: String?,
    syncActiveModel: Boolean = (Settings.ai_agent == agent.name),
) {
    val resolved = resolveModelForAgent(agent, model).orEmpty()
    writeModelSlot(agent.name, resolved)
    if (syncActiveModel) {
        Settings.ai_model = resolved
    }
}
