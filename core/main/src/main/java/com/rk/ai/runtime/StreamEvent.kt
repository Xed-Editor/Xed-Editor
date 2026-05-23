package com.rk.ai.runtime

sealed class StreamEvent {
    data class Token(val text: String) : StreamEvent()
    data class ToolCall(val name: String, val args: String, val callId: String) : StreamEvent()
    data class ToolResult(val callId: String, val output: String, val error: String? = null) : StreamEvent()
    data class Error(val message: String, val code: Int = -1) : StreamEvent()
    data class Done(val finishReason: String = "stop") : StreamEvent()
    data object StreamStart : StreamEvent()
    data class Status(val message: String) : StreamEvent()
}

data class ExecutionResult(
    val success: Boolean,
    val output: String,
    val toolCalls: List<StreamEvent.ToolCall> = emptyList(),
    val error: String? = null,
    val durationMs: Long = 0,
)
