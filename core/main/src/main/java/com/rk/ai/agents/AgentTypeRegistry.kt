package com.rk.ai.agents

import com.rk.settings.Settings
import java.util.concurrent.ConcurrentHashMap

object AgentTypeRegistry {
    private val agents = ConcurrentHashMap<String, AiAgent>()

    init {
        register(GeminiAgent)
        register(OpenCodeAgent)
    }

    fun register(agent: AiAgent) {
        agents[agent.name] = agent
    }

    fun resolve(type: String? = null): AiAgent {
        val requested = type ?: Settings.ai_agent.takeIf { it.isNotBlank() }
        return agents[requested] ?: agents.values.firstOrNull()
            ?: GeminiAgent
    }

    fun available(): List<AiAgent> = agents.values.toList()

    fun all(): Map<String, AiAgent> = agents.toMap()

    fun get(name: String): AiAgent? = agents[name]
}
