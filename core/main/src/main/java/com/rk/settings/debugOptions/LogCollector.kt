package com.rk.settings.debugOptions

import com.rk.DefaultScope
import com.rk.events.AppEvent
import com.rk.events.Events
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LogLevel(val label: String, val value: Int) {
    ERROR(strings.error.getString(), 1),
    WARN(strings.warning.getString(), 2),
    INFO(strings.info.getString(), 3),
    DEBUG(strings.debug.getString(), 5),
}

data class LogEntry(val level: LogLevel, val message: String, val timestamp: Long = System.currentTimeMillis())

object LogCollector {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun reportDebug(message: String, extensionId: String? = null) {
        appendEntry(
            LogEntry(
                LogLevel.DEBUG,
                buildMessage(message, extensionId),
            ),
            extensionId,
        )
    }

    fun reportInfo(message: String, extensionId: String? = null) {
        appendEntry(
            LogEntry(
                LogLevel.INFO,
                buildMessage(message, extensionId),
            ),
            extensionId,
        )
    }

    fun reportWarn(message: String, extensionId: String? = null) {
        appendEntry(
            LogEntry(
                LogLevel.WARN,
                buildMessage(message, extensionId),
            ),
            extensionId,
        )
    }

    fun reportError(message: String, extensionId: String? = null) {
        appendEntry(
            LogEntry(
                LogLevel.ERROR,
                buildMessage(message, extensionId),
            ),
            extensionId,
        )
    }

    private fun buildMessage(message: String, extensionId: String? = null): String {
        return extensionId?.let {
            "[${extensionId}] $message"
        } ?: message
    }

    private fun appendEntry(logEntry: LogEntry, extensionId: String? = null) {
        _logs.update { it + logEntry }
        DefaultScope.launch {
            Events.publish(AppEvent.LogEntryWritten(logEntry, extensionId))
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
