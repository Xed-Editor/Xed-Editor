package com.rk.core.diagnostics

import android.util.Log
import com.rk.ai.IdeBridge
import com.rk.ai.runtime.AiRequestTracker
import com.rk.ai.session.AiSessionManager
import com.rk.core.performance.MemoryGuard
import com.rk.xededitor.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val thread: String = Thread.currentThread().name,
)

object DebugConsole {
    private const val TAG = "DebugConsole"
    private const val MAX_LOG_ENTRIES = 500
    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private val listeners = mutableListOf<(LogEntry) -> Unit>()

    private val dateFormat by lazy { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    init {
        if (BuildConfig.DEBUG) {
            log(LogLevel.INFO, TAG, "Debug console initialized")
        }
    }

    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(level = level, tag = tag, message = message)
        logEntries.add(entry)
        if (logEntries.size > MAX_LOG_ENTRIES) {
            logEntries.poll()
        }
        listeners.forEach { it(entry) }
        when (level) {
            LogLevel.ERROR -> Log.e(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.DEBUG -> if (BuildConfig.DEBUG) Log.d(tag, message)
        }
    }

    fun d(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(LogLevel.WARN, tag, msg)
    fun e(tag: String, msg: String) = log(LogLevel.ERROR, tag, msg)

    fun getLogs(level: LogLevel? = null, tag: String? = null, maxCount: Int = 100): List<LogEntry> {
        return logEntries
            .filter { level == null || it.level == level }
            .filter { tag == null || it.tag.contains(tag, ignoreCase = true) }
            .takeLast(maxCount)
            .toList()
    }

    fun getRecentLogs(count: Int = 50): List<LogEntry> =
        logEntries.toList().takeLast(count)

    fun clear() = logEntries.clear()

    fun onLog(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    fun dumpToFile(file: File) {
        file.bufferedWriter().use { writer ->
            logEntries.forEach { entry ->
                writer.write("${dateFormat.format(Date(entry.timestamp))} [${entry.level}] ${entry.tag}: ${entry.message}")
                writer.newLine()
            }
        }
    }

    fun systemDiagnostic(): String = buildString {
        appendLine("=== System Diagnostic ===")
        appendLine()
        appendLine("--- AI System ---")
        appendLine("  Agent: ${AiSessionManager.currentAgent.name}")
        appendLine("  Status: ${AiSessionManager.connectionStatus}")
        appendLine("  Bridge: ${if (IdeBridge.isRunning()) "running" else "stopped"}")
        appendLine("  Bridge Clients: ${IdeBridge.connectedClients()}")
        try {
            val mcp = IdeBridge.checkMcpConnection()
            appendLine("  MCP: ${if (mcp.first) "OK" else "FAIL"}: ${mcp.second}")
        } catch (e: Exception) {
            appendLine("  MCP: ERROR: ${e.message}")
        }
        appendLine()
        AiRequestTracker.let { tracker ->
            appendLine("--- AI Requests ---")
            appendLine("  Total: ${tracker.totalRequests}")
            appendLine("  Successful: ${tracker.successfulRequests}")
            appendLine("  Failed: ${tracker.failedRequests}")
            appendLine("  Active: ${tracker.activeCount()}")
            appendLine("  Total Tokens: ${tracker.totalTokens}")
        }
        appendLine()
        appendLine("--- Performance ---")
        MemoryGuard.let { mg ->
            appendLine("  Free: ${mg.lastFreeMemoryMB}MB")
            appendLine("  Total: ${mg.lastTotalMemoryMB}MB")
            appendLine("  Max: ${mg.maxMemoryMB()}MB")
        }
        appendLine()
        appendLine("--- Log Stats ---")
        appendLine("  Total Logs: ${logEntries.size}")
        appendLine("  Errors: ${logEntries.count { it.level == LogLevel.ERROR }}")
        appendLine("  Warnings: ${logEntries.count { it.level == LogLevel.WARN }}")
        appendLine()
        appendLine("--- Recent Logs (last 20) ---")
        getRecentLogs(20).forEach { entry ->
            appendLine("  ${dateFormat.format(Date(entry.timestamp))} [${entry.level.name}] ${entry.tag}: ${entry.message.take(200)}")
        }
    }

    fun crashContext(): Map<String, Any> = mapOf(
        "agent" to AiSessionManager.currentAgent.name,
        "connectionStatus" to AiSessionManager.connectionStatus.name,
        "bridgeRunning" to IdeBridge.isRunning(),
        "bridgeClients" to IdeBridge.connectedClients(),
        "freeMemory" to MemoryGuard.lastFreeMemoryMB,
        "totalMemory" to MemoryGuard.lastTotalMemoryMB,
        "totalAiRequests" to AiRequestTracker.totalRequests,
        "failedAiRequests" to AiRequestTracker.failedRequests,
        "logCount" to logEntries.size,
        "errorCount" to logEntries.count { it.level == LogLevel.ERROR },
    )
}

class DiagnosticCollector {
    private val collectors = mutableMapOf<String, suspend () -> String>()

    fun register(name: String, collector: suspend () -> String) {
        collectors[name] = collector
    }

    fun unregister(name: String) {
        collectors.remove(name)
    }

    suspend fun collectAll(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        collectors.forEach { (name, collector) ->
            try {
                result[name] = collector()
            } catch (e: Exception) {
                result[name] = "ERROR: ${e.message}"
            }
        }
        return result
    }

    suspend fun collect(vararg names: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        names.forEach { name ->
            collectors[name]?.let { collector ->
                try {
                    result[name] = collector()
                } catch (e: Exception) {
                    result[name] = "ERROR: ${e.message}"
                }
            }
        }
        return result
    }
}
