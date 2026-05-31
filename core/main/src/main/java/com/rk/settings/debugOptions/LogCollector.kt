package com.rk.settings.debugOptions

import androidx.compose.runtime.mutableStateListOf
import com.rk.resources.getString
import com.rk.resources.strings

enum class LogLevel(val label: String, val value: Int) {
    ERROR(strings.error.getString(), 1),
    WARN(strings.warning.getString(), 2),
    INFO(strings.info.getString(), 3),
    DEBUG(strings.debug.getString(), 5),
}

data class LogEntry(val level: LogLevel, val message: String, val timestamp: Long = System.currentTimeMillis())

object LogCollector {
    val logs = mutableStateListOf<LogEntry>()

    fun reportDebug(message: String) {
        logs.add(LogEntry(LogLevel.DEBUG, message))
    }

    fun reportInfo(message: String) {
        logs.add(LogEntry(LogLevel.INFO, message))
    }

    fun reportWarn(message: String) {
        logs.add(LogEntry(LogLevel.WARN, message))
    }

    fun reportError(message: String) {
        logs.add(LogEntry(LogLevel.ERROR, message))
    }

    fun clearLogs() {
        logs.clear()
    }
}
