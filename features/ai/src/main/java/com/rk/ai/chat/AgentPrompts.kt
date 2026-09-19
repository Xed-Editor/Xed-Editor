package com.rk.ai.chat

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.MessagePart
import com.rk.ai.model.AiTodo
import com.rk.ai.model.AiToolCall
import com.rk.ai.model.AiTurn
import com.rk.ai.model.TodoStatus
import com.rk.ai.settings.AiMemory
import kotlinx.coroutines.CancellationException

/**
 * Builds the system prompt for a run: the configured prompt plus the state the agent needs to carry
 * between turns - long-term memory, the declared goal and the task list. The scratch pad is
 * deliberately not included; the agent reads that back on demand.
 */
internal fun buildSystemPrompt(base: String, goal: String?, todos: List<AiTodo>): String =
    buildString {
        append(base)
        AiMemory.promptSection()?.let { append("\n\n").append(it) }
        goal?.let { append("\n\nThe goal for this chat is: ").append(it) }
        if (todos.isNotEmpty()) {
            append("\n\nCurrent task list:")
            todos.forEach { todo ->
                val mark = if (todo.status == TodoStatus.Completed) "x" else " "
                append("\n- [").append(mark).append("] ").append(todo.content)
            }
        }
    }

/** Replays the conversation history as a koog prompt. */
internal fun buildConversationPrompt(systemPrompt: String, history: List<AiTurn>): Prompt =
    prompt("xed-ai-chat") {
        system(systemPrompt)
        history.forEach { turn ->
            when (turn) {
                is AiTurn.User -> user(turn.text)
                is AiTurn.Assistant -> {
                    val parts = turn.toResponseParts()
                    if (parts.isNotEmpty()) assistant(parts)
                }
                is AiTurn.ToolOutput -> toolResult(turn.name, turn.output, turn.id, turn.isError)
            }
        }
    }

/** One assistant turn's text and tool calls, as koog response parts. */
internal fun AiTurn.Assistant.toResponseParts(): List<MessagePart.ResponsePart> = buildList {
    if (text.isNotBlank()) add(MessagePart.Text(text))
    toolCalls.forEach { call ->
        add(MessagePart.Tool.Call(id = call.id, tool = call.name, args = call.args))
    }
}

/**
 * Closes out the tool calls from [fromIndex] onwards after one of them aborted the run.
 *
 * The assistant turn that requested them is already in the history, and the API requires a tool
 * result for every `tool_call_id` in it. Without these the *next* request is rejected with "an
 * assistant message with 'tool_calls' must be followed by tool messages". This covers a user
 * denial, the stop button cancelling mid-tool, and any unexpected failure.
 */
internal fun answerRemainingToolCalls(
    history: MutableList<AiTurn>,
    calls: List<AiToolCall>,
    fromIndex: Int,
    cause: Throwable,
) {
    val reason =
        when (cause) {
            is ToolDeniedException -> "Not run: the user denied this action."
            is CancellationException -> "Not run: the user stopped the run."
            else -> "Not run: ${cause.message ?: "the run failed"}"
        }
    calls.drop(fromIndex).forEach { call ->
        history.add(AiTurn.ToolOutput(call.id, call.name, reason, true))
    }
}
