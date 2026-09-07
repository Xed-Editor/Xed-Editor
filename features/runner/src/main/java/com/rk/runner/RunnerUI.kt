package com.rk.runner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object RunnerUI {
    var runnersToShow by mutableStateOf<List<RunnableOption>>(emptyList())
    var showRunnerDialog by mutableStateOf(false)
}
