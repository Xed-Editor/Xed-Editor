package com.rk.ai.chat

enum class AiChatRole {
    User,
    Assistant,
    Tool,
}

enum class ToolCallStatus {
    AwaitingApproval,
    Running,
    Success,
    Failed,
    Denied,
}

data class AiChatMessage(
    val id: Long,
    val role: AiChatRole,
    val text: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolStatus: ToolCallStatus? = null,
    val diff: String? = null,
)

data class PendingApproval(
    val toolName: String,
    val args: String,
    val description: String,
    val diff: String? = null,
    val filePath: String? = null,
)

data class AiToolCall(val id: String, val name: String, val args: String)

sealed interface AiTurn {
    data class User(val text: String) : AiTurn

    data class Assistant(val text: String, val toolCalls: List<AiToolCall>) : AiTurn

    data class ToolOutput(
        val id: String,
        val name: String,
        val output: String,
        val isError: Boolean,
    ) : AiTurn
}
