package com.rk.ai.nativeagent.engine

import com.rk.ai.agent.events.SessionTodo
import com.rk.ai.agent.events.SessionTodoStatus

data class TaskState(
    val todos: List<SessionTodo> = emptyList(),
) {
    val completedTodos: Int get() = todos.count { it.status == SessionTodoStatus.COMPLETED }
    val pendingTodos: Int get() = todos.count { it.status == SessionTodoStatus.PENDING }
}
