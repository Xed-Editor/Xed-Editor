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

/** Base prompt plus memory, goal and task list; the scratch pad is read back on demand instead. */
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

internal fun AiTurn.Assistant.toResponseParts(): List<MessagePart.ResponsePart> = buildList {
    if (text.isNotBlank()) add(MessagePart.Text(text))
    toolCalls.forEach { call ->
        add(MessagePart.Tool.Call(id = call.id, tool = call.name, args = call.args))
    }
}

/** Answers the tool calls from [fromIndex] on, which the API requires once the assistant turn exists. */
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
