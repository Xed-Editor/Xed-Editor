package com.rk.ai.agent.agents

import com.rk.ai.models.UIMessagePart

data class AgentCapability(
    val name: String,
    val description: String,
    val inputSchema: String,
)

data class AgentTask(
    val id: String,
    val prompt: String,
    val contextMessages: List<String> = emptyList(),
    val maxSteps: Int = 20,
)

sealed class AgentResult {
    data class Success(
        val output: String,
        val filesCreated: List<String> = emptyList(),
        val filesModified: List<String> = emptyList(),
        val summary: String = "",
    ) : AgentResult()

    data class Failure(val error: String) : AgentResult()
    data object NotAttempted : AgentResult()
}

interface SubAgent {
    val name: String
    val description: String
    val capabilities: List<AgentCapability>

    suspend fun execute(task: AgentTask): AgentResult
    suspend fun isAvailable(): Boolean = true
}
