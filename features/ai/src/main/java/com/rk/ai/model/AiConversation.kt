package com.rk.ai.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class AiChatRole {
    User,
    Assistant,
    Tool,
}

enum class ToolCallStatus {
    AwaitingApproval,

    AwaitingInput,
    Running,
    Success,
    Failed,
    Denied,

    /** The process went away with this call in flight; restoring a session rewrites it to this. */
    Interrupted,
}

data class AiChatMessage(
    val id: Long,
    val role: AiChatRole,
    val text: String = "",
    /** Streamed reasoning ("thinking"), kept apart from [text] so it can be folded away. */
    val reasoning: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolStatus: ToolCallStatus? = null,
    val diff: String? = null,
    /** The steps a sub-agent produced while running this tool call. */
    val children: List<AiChatMessage> = emptyList(),
)

data class PendingApproval(val toolName: String)

data class PendingQuestion(val question: String, val options: List<String>)

/** A tool call the model asked for, exactly as it streamed it. */
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

/** Reads the `options` array of an `ask_user` call, tolerating anything malformed. */
internal fun parseOptions(element: JsonElement?): List<String> {
    val array = element as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        (item as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }
}
