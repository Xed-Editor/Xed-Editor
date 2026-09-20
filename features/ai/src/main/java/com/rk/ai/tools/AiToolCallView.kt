package com.rk.ai.tools

import com.rk.ai.model.AiTodo
import com.rk.ai.model.parseOptions
import com.rk.ai.model.parseTodos
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

sealed interface ToolBodyPart {
    data class Field(val label: String, val value: String) : ToolBodyPart

    data class Block(val label: String, val code: String) : ToolBodyPart
}

data class ToolCallView(
    val action: String,
    val target: String?,
    val arguments: List<ToolBodyPart>,
)

fun interface AiToolPresenter {
    fun present(args: JsonObject): ToolCallView
}

private const val INLINE_VALUE_LIMIT = 100

fun toolDetail(label: String, value: String?): ToolBodyPart? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return if (text.contains('\n') || text.length > INLINE_VALUE_LIMIT) {
        ToolBodyPart.Block(label, text)
    } else {
        ToolBodyPart.Field(label, text)
    }
}

fun toolField(label: String, value: String?): ToolBodyPart? =
    value?.takeIf { it.isNotBlank() }?.let { ToolBodyPart.Field(label, it) }

fun toolBlock(label: String, value: String?): ToolBodyPart? =
    value?.takeIf { it.isNotBlank() }?.let { ToolBodyPart.Block(label, it) }

fun toolLargeBlock(label: String, value: String?): ToolBodyPart? =
    value
        ?.takeIf { it.isNotBlank() && (it.contains('\n') || it.length > INLINE_VALUE_LIMIT) }
        ?.let { ToolBodyPart.Block(label, it) }

fun toolCallView(toolName: String, rawArgs: String?): ToolCallView {
    val args = parseToolArgs(rawArgs)
    val presenter = AiToolRegistry.find(toolName)?.presenter
    return presenter?.present(args) ?: genericView(toolName, args)
}

private fun genericView(toolName: String, args: JsonObject): ToolCallView {
    val entries = args.entries.mapNotNull { (key, element) ->
        toolDetail(humanize(key), (element as? JsonPrimitive)?.contentOrNull ?: element.toString())
    }
    val target =
        args.entries.firstNotNullOfOrNull { (_, element) ->
            (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
    return ToolCallView(action = humanize(toolName), target = target, arguments = entries)
}

internal fun parseToolArgs(raw: String?): JsonObject {
    if (raw.isNullOrBlank()) return JsonObject(emptyMap())
    return runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrDefault(JsonObject(emptyMap()))
}

internal fun JsonObject.lineRange(): ToolBodyPart? {
    val start = displayArg("start_line")
    val end = displayArg("end_line")
    return when {
        start != null && end != null -> ToolBodyPart.Field(strings.ai_tool_lines.getString(), "$start–$end")
        start != null -> ToolBodyPart.Field(strings.ai_tool_lines.getString(), strings.ai_tool_lines_from.getFilledString(start))
        end != null -> ToolBodyPart.Field(strings.ai_tool_lines.getString(), strings.ai_tool_lines_up_to.getFilledString(end))
        else -> null
    }
}

internal fun JsonObject.pairOfPaths(): String? {
    val from = displayArg("from")
    val to = displayArg("to")
    return when {
        from != null && to != null -> "$from → $to"
        else -> from ?: to
    }
}

internal fun todosView(todos: List<AiTodo>): ToolCallView =
    ToolCallView(
        action = strings.ai_tool_update_tasks.getString(),
        target =
            when (todos.size) {
                0 -> strings.ai_tool_list_cleared.getString()
                1 -> strings.ai_tool_one_task.getString()
                else -> strings.ai_tool_tasks.getFilledString(todos.size)
            },
        arguments = todos.map { ToolBodyPart.Field(it.status.label, it.content) },
    )

internal fun questionView(question: String?, options: List<String>): ToolCallView =
    ToolCallView(
        action = strings.ai_tool_ask_you.getString(),
        target = question,
        arguments =
            options.mapIndexed { index, option ->
                ToolBodyPart.Field(strings.ai_tool_option.getFilledString(index + 1), option)
            },
    )

internal fun writeTodosView(args: JsonObject): ToolCallView = todosView(parseTodos(args["todos"]))

internal fun askUserView(args: JsonObject): ToolCallView =
    questionView(args.displayArg("question"), parseOptions(args["options"]))

internal fun humanize(name: String): String {
    val words = name.replace('_', ' ').trim()
    if (words.isEmpty()) return name
    return words.replaceFirstChar { it.uppercaseChar() }
}
