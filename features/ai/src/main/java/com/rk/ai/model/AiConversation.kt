package com.rk.ai.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The conversation-level models shared by the agent loop, the tools and the chat UI.
 *
 * They deliberately live outside `com.rk.ai.chat` and `com.rk.ai.tools` so that neither package has
 * to depend on the other: tools describe their work with these types and the UI renders them.
 */
enum class AiChatRole {
    User,
    Assistant,
    Tool,
}

/** Lifecycle of a single tool call as it is shown in the transcript. */
enum class ToolCallStatus {
    AwaitingApproval,

    /** The run is suspended waiting for the user to answer a question. */
    AwaitingInput,
    Running,
    Success,
    Failed,
    Denied,
}

data class AiChatMessage(
    val id: Long,
    val role: AiChatRole,
    val text: String = "",
    /**
     * Streamed reasoning ("thinking") for an assistant turn. Kept apart from [text] so it can be
     * folded away instead of mixing into the answer.
     */
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

/** A question the agent is blocked on, with the options (if any) it offered. */
data class PendingQuestion(val question: String, val options: List<String>)

/** A tool call the model asked for, exactly as it streamed it. */
data class AiToolCall(val id: String, val name: String, val args: String)

/** One turn of the replayed conversation history. */
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
