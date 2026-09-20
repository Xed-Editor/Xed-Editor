package com.rk.ai.model

import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class TodoStatus {
    Pending,
    InProgress,
    Completed,
    ;

    val label: String
        get() =
            when (this) {
                Pending -> strings.ai_todo_pending.getString()
                InProgress -> strings.ai_todo_in_progress.getString()
                Completed -> strings.ai_todo_completed.getString()
            }

    companion object {
        fun from(value: String?): TodoStatus =
            when (value?.trim()?.lowercase()?.replace('-', '_')) {
                "in_progress", "inprogress", "doing", "active" -> InProgress
                "completed", "complete", "done" -> Completed
                else -> Pending
            }
    }
}

data class AiTodo(val content: String, val status: TodoStatus)

/** Reads the `todos` array of a `write_todos` call, tolerating anything malformed. */
internal fun parseTodos(element: JsonElement?): List<AiTodo> {
    val array = element as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        val row = item as? JsonObject ?: return@mapNotNull null
        val content = (row["content"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (content.isEmpty()) null
        else AiTodo(content, TodoStatus.from((row["status"] as? JsonPrimitive)?.contentOrNull))
    }
}
