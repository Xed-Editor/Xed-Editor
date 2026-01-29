package com.rk.settings.debugOptions

import androidx.compose.runtime.mutableStateListOf

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class AppLogEntry(val level: LogLevel, val message: String, val timestamp: Long = System.currentTimeMillis())

object LogCollector {
    val logs = mutableStateListOf<AppLogEntry>()

    fun reportDebug(message: String) {
        logs.add(AppLogEntry(LogLevel.DEBUG, message))
    }

    fun reportInfo(message: String) {
        logs.add(AppLogEntry(LogLevel.INFO, message))
    }

    fun reportWarn(message: String) {
        logs.add(AppLogEntry(LogLevel.WARN, message))
    }

    fun reportError(message: String) {
        logs.add(AppLogEntry(LogLevel.ERROR, message))
    }

    fun clearLogs() {
        logs.clear()
    }
}
