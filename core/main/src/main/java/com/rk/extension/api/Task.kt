package com.rk.extension.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Interface for a task that can be shown in the floating output view. */
@XedExtensionPoint
interface Task {
    val id: String
    val label: String
    val icon: @Composable () -> Unit
    val isRunning: Boolean
    val output: String
    val latestLine: String
    val progress: Float? // null for indeterminate
    val exitCode: Int?

    fun stop()

    fun dismiss()
}

/** Base implementation of a Task. */
@XedExtensionPoint
open class BaseTask(
    override val id: String,
    override val label: String,
    override val icon: @Composable () -> Unit,
) : Task {
    override var isRunning by mutableStateOf(true)
    override var output by mutableStateOf("")
    override var latestLine by mutableStateOf("")
    override var progress by mutableStateOf<Float?>(null)
    override var exitCode by mutableStateOf<Int?>(null)

    private var onStop: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    override fun stop() {
        onStop?.invoke()
        isRunning = false
    }

    override fun dismiss() {
        onDismiss?.invoke()
        TaskRegistry.removeTask(this)
    }

    fun updateOutput(line: String) {
        output += line + "\n"
        latestLine = line
    }

    fun setOnStop(block: (() -> Unit)?) {
        onStop = block
    }

    fun setOnDismiss(block: (() -> Unit)?) {
        onDismiss = block
    }
}

/** Registry for tasks shown in the floating output view. */
@XedExtensionPoint
object TaskRegistry {
    private val _tasks = MutableStateFlow(listOf<Task>())
    val tasks: StateFlow<List<Task>>
        get() = _tasks.asStateFlow()

    fun addTask(task: Task) {
        if (_tasks.value.any { it.id == task.id }) return
        _tasks.update { it + task }
    }

    fun removeTask(task: Task) {
        _tasks.update { it - task }
    }
}
