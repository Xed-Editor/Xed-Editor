package com.rk.ai.agent.context

import com.rk.ai.agent.planner.TaskNode
import com.rk.ai.agent.planner.TaskTree

data class WorkingState(
    val currentFile: String = "",
    val currentSymbol: String = "",
    val recentEdits: List<EditRecord> = emptyList(),
    val reasoning: List<String> = emptyList(),
    val lastActionResult: String = "",
)

data class EditRecord(
    val file: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
)

class WorkingMemory {
    private var currentTask: TaskNode? = null
    private var taskTree: TaskTree? = null
    private var state = WorkingState()
    private val sessionLog = mutableListOf<String>()

    fun setCurrentTask(task: TaskNode) { currentTask = task }
    fun getCurrentTask(): TaskNode? = currentTask

    fun setTaskTree(tree: TaskTree) { taskTree = tree }
    fun getTaskTree(): TaskTree? = taskTree

    fun updateState(transform: WorkingState.() -> WorkingState) { state = state.transform() }
    fun getState(): WorkingState = state

    fun log(message: String) {
        sessionLog.add("[${java.time.Instant.now()}] $message")
        if (sessionLog.size > 200) sessionLog.removeAt(0)
    }

    fun getRecentLogs(count: Int = 20): List<String> = sessionLog.takeLast(count)

    fun recordEdit(file: String, action: String) {
        val edits = state.recentEdits.toMutableList()
        edits.add(EditRecord(file, action))
        state = state.copy(recentEdits = edits.takeLast(50))
    }

    fun getModifiedFiles(): List<String> = state.recentEdits.map { it.file }.distinct()

    fun clear() {
        currentTask = null
        taskTree = null
        state = WorkingState()
        sessionLog.clear()
    }
}
