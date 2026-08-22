package com.rk.runner

import kotlinx.coroutines.flow.MutableStateFlow

object RunnerUI {
    val runnersToShow = MutableStateFlow<List<RunnableOption>>(emptyList())

    val showRunnerDialog = MutableStateFlow(false)
}
