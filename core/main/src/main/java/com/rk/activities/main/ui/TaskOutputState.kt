package com.rk.activities.main.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.extension.api.Task
import com.rk.extension.api.TaskRegistry

object TaskOutputState {
    var expanded by mutableStateOf(false)
    var focusedTask by mutableStateOf<Task?>(null)

    val isActive: Boolean
        get() = TaskRegistry.tasks.value.isNotEmpty()

    fun updateActiveTask() {
        val task = focusedTask
        if (task == null || task !in TaskRegistry.tasks.value || !task.isRunning) {
            focusedTask = TaskRegistry.tasks.value.lastOrNull { it.isRunning } ?: TaskRegistry.tasks.value.lastOrNull()
        }
    }
}
