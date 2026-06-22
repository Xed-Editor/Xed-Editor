package com.rk.ai.agents

import com.rk.settings.Settings

object AgentTypeRegistry {
    private val agents = mutableMapOf<String, AiAgent>()

    init {
        agents["gemini"] = GeminiAgent
        agents["opencode"] = OpenCodeAgent
        agents["antigravity"] = AntigravityAgent
        agents["codex"] = CodexAgent
        agents["claude"] = ClaudeCodeAgent
    }

    fun register(agent: AiAgent) {
        agents[agent.name] = agent
    }

    fun resolve(type: String? = null): AiAgent =
        agents[type ?: Settings.ai_agent] ?: GeminiAgent

    fun available(): List<AiAgent> = agents.values.toList()

    fun all(): Map<String, AiAgent> = agents.toMap()

    fun get(name: String): AiAgent? = agents[name]
}
