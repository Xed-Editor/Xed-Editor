package com.rk.activities.main.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.extension.api.Task
import com.rk.extension.api.TaskRegistry

object TaskOutputState {
    var expanded by mutableStateOf(false)
    var activeTask by mutableStateOf<Task?>(null)

    val isActive: Boolean
        get() = TaskRegistry.tasks.value.isNotEmpty()

    fun updateActiveTask() {
        if (activeTask == null || activeTask !in TaskRegistry.tasks.value) {
            activeTask = TaskRegistry.tasks.value.lastOrNull()
        }
    }
}
