package com.rk.ai.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.rk.ai.providers.ProviderSetting
import com.rk.ai.providers.TextGenerationParams
import com.rk.ai.models.UIMessage

sealed class AILogging {
    data class Generation(
        val params: TextGenerationParams,
        val messages: List<UIMessage>,
        val providerSetting: ProviderSetting,
        val stream: Boolean,
    ) : AILogging()
}

private const val MAX_LOGS = 32

class AILoggingManager {
    private val logs = MutableStateFlow<List<AILogging>>(emptyList())

    fun getLogs(): StateFlow<List<AILogging>> = logs

    fun addLog(log: AILogging) {
        logs.value = logs.value + log
        if (logs.value.size > MAX_LOGS) {
            logs.value = logs.value.drop(1)
        }
    }

    fun clearLogs() {
        logs.value = emptyList()
    }
}
