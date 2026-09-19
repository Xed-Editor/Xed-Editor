package com.rk.ai.tools

import com.rk.ai.model.AiTodo
import com.rk.ai.model.parseOptions
import com.rk.ai.model.parseTodos
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * One item in the expanded body of a tool call.
 *
 * [Field] is a short scalar (a path, a timeout, a query) and is rendered as a label/value row.
 * [Block] is long or multi-line content (file body, shell command, diff payload) and is rendered as
 * a monospace panel, because a row would either wrap badly or hide most of the value.
 */
sealed interface ToolBodyPart {
    data class Field(val label: String, val value: String) : ToolBodyPart

    data class Block(val label: String, val code: String) : ToolBodyPart
}

/**
 * A human-readable description of a single tool call.
 *
 * The raw tool arguments are JSON written for the model, not for a person: showing them verbatim
 * means the reader has to decode `{"path":"a/b.kt","start_line":"10"}` in their head. This model is
 * the translation layer - [action] names what the tool did, [target] is the one value worth putting
 * in the header (usually the path, command or URL the call is about), and [arguments] carries the
 * remaining parameters for the expanded body.
 */
data class ToolCallView(
    val action: String,
    val target: String?,
    val arguments: List<ToolBodyPart>,
)

/**
 * Turns a tool's arguments into the transcript view for the call.
 *
 * Every [AiTool] may carry one; tools that do not get the generic key/value fallback, so a newly
 * registered tool still renders sensibly without any UI work.
 */
fun interface AiToolPresenter {
    fun present(args: JsonObject): ToolCallView
}

/** Values longer than this, or spanning lines, become a [ToolBodyPart.Block] instead of a row. */
private const val INLINE_VALUE_LIMIT = 100

/** Short values stay a row; long or multi-line ones become a panel. */
fun toolDetail(label: String, value: String?): ToolBodyPart? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return if (text.contains('\n') || text.length > INLINE_VALUE_LIMIT) {
        ToolBodyPart.Block(label, text)
    } else {
        ToolBodyPart.Field(label, text)
    }
}

/** Short scalar values that always belong in the body. */
fun toolField(label: String, value: String?): ToolBodyPart? =
    value?.takeIf { it.isNotBlank() }?.let { ToolBodyPart.Field(label, it) }

/** Content that must be shown as a panel regardless of length, such as a file body. */
fun toolBlock(label: String, value: String?): ToolBodyPart? =
    value?.takeIf { it.isNotBlank() }?.let { ToolBodyPart.Block(label, it) }

/** Content that only needs a panel when the header could not show all of it. */
fun toolLargeBlock(label: String, value: String?): ToolBodyPart? =
    value
        ?.takeIf { it.isNotBlank() && (it.contains('\n') || it.length > INLINE_VALUE_LIMIT) }
        ?.let { ToolBodyPart.Block(label, it) }

/**
 * Maps a tool call to its human-readable view.
 *
 * The tool's own [AiToolPresenter] is used when it has one - built-in tools and extension tools
 * alike - and anything unknown falls back to a generic key/value listing.
 */
fun toolCallView(toolName: String, rawArgs: String?): ToolCallView {
    val args = parseToolArgs(rawArgs)
    val presenter = AiToolRegistry.find(toolName)?.presenter
    return presenter?.present(args) ?: genericView(toolName, args)
}

/** Last-resort rendering for a tool this module does not know about. */
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
        start != null && end != null -> ToolBodyPart.Field("Lines", "$start–$end")
        start != null -> ToolBodyPart.Field("Lines", "from $start")
        end != null -> ToolBodyPart.Field("Lines", "up to $end")
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

/** The task list as a view, used by the `write_todos` presenter. */
internal fun todosView(todos: List<AiTodo>): ToolCallView =
    ToolCallView(
        action = "Update tasks",
        target =
            when (todos.size) {
                0 -> "list cleared"
                1 -> "1 task"
                else -> "${todos.size} tasks"
            },
        arguments = todos.map { ToolBodyPart.Field(it.status.label, it.content) },
    )

/** The question and its options as a view, for the `ask_user` presenter. */
internal fun questionView(question: String?, options: List<String>): ToolCallView =
    ToolCallView(
        action = "Ask you",
        target = question,
        arguments = options.mapIndexed { index, option -> ToolBodyPart.Field("Option ${index + 1}", option) },
    )

/** Parses the `todos` argument of a `write_todos` call into the transcript view. */
internal fun writeTodosView(args: JsonObject): ToolCallView = todosView(parseTodos(args["todos"]))

/** Parses the `options` argument of an `ask_user` call into the transcript view. */
internal fun askUserView(args: JsonObject): ToolCallView =
    questionView(args.displayArg("question"), parseOptions(args["options"]))

/** `run_android_shell` -> "Run android shell", `read_file` -> "Read file". */
internal fun humanize(name: String): String {
    val words = name.replace('_', ' ').trim()
    if (words.isEmpty()) return name
    return words.replaceFirstChar { it.uppercaseChar() }
}
