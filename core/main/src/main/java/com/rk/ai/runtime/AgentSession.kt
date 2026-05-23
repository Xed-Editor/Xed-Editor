package com.rk.ai.runtime

import com.rk.ai.agents.AiAgent

data class AgentSessionConfig(
    val agent: AiAgent? = null,
    val model: String = "",
    val workingDir: String = "/",
)

enum class SessionPhase {
    IDLE,
    INITIALIZING,
    RUNNING,
    STREAMING,
    WAITING_FOR_TOOL,
    CANCELLING,
    ERROR,
    DONE,
}

data class AgentSessionState(
    val id: String = "",
    val phase: SessionPhase = SessionPhase.IDLE,
    val config: AgentSessionConfig = AgentSessionConfig(),
    val events: List<StreamEvent> = emptyList(),
    val accumulatedText: String = "",
    val error: String? = null,
    val toolCalls: Map<String, StreamEvent.ToolCall> = emptyMap(),
    val toolResults: Map<String, StreamEvent.ToolResult> = emptyMap(),
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val isCancelled: Boolean = false,
)

fun AgentSessionState.addEvent(event: StreamEvent): AgentSessionState {
    val newEvents = (events + event).takeLast(10000)
    val newText = when (event) {
        is StreamEvent.Token -> accumulatedText + event.text
        else -> accumulatedText
    }
    val newToolCalls = when (event) {
        is StreamEvent.ToolCall -> toolCalls + (event.callId to event)
        else -> toolCalls
    }
    val newToolResults = when (event) {
        is StreamEvent.ToolResult -> toolResults + (event.callId to event)
        else -> toolResults
    }
    return copy(
        events = newEvents,
        accumulatedText = newText,
        toolCalls = newToolCalls,
        toolResults = newToolResults,
    )
}
