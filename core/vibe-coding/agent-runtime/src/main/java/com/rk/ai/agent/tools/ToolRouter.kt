package com.rk.ai.agent.tools

import android.util.Log
import com.rk.ai.agent.context.ProjectMemory
import com.rk.ai.models.ExecutionState
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.google.gson.JsonParser

private const val TAG = "ToolRouter"

data class ToolCallRecord(
    val toolName: String,
    val argsHash: String,
    val timestamp: Long,
    val durationMs: Long,
    val success: Boolean,
    val fromCache: Boolean,
)

class ToolRouter(
    private val cache: ToolCache,
    private val projectMemory: ProjectMemory? = null,
) {
    data class ToolStats(
        var callCount: Int = 0,
        var cacheHitCount: Int = 0,
        var totalDurationMs: Long = 0,
        var errorCount: Int = 0,
    )

    private val stats = mutableMapOf<String, ToolStats>()
    private val executionHistory = mutableListOf<ToolCallRecord>()

    fun recordExecution(
        toolName: String,
        args: String,
        durationMs: Long,
        success: Boolean,
        fromCache: Boolean,
    ) {
        val record = ToolCallRecord(toolName, args.take(100), System.currentTimeMillis(), durationMs, success, fromCache)
        executionHistory.add(record)
        if (executionHistory.size > 200) executionHistory.removeAt(0)

        val s = stats.getOrPut(toolName) { ToolStats() }
        s.callCount++
        if (fromCache) s.cacheHitCount++
        s.totalDurationMs += durationMs
        if (!success) s.errorCount++
    }

    fun getStats(): Map<String, ToolStats> = stats.toMap()
    fun getHistory(): List<ToolCallRecord> = executionHistory.toList()

    fun hasBeenCalled(toolName: String, argsHash: String): Boolean {
        return executionHistory.any { it.toolName == toolName && it.argsHash == argsHash }
    }

    fun countCalls(toolName: String): Int = stats[toolName]?.callCount ?: 0

    fun needsProjectRefresh(): Boolean {
        val projectToolCalls = listOf("getProjectStructure", "getProjectSummary")
        return projectToolCalls.any { name ->
            stats[name]?.let { it.callCount > 0 && it.callCount % 5 == 0 } ?: false
        }
    }

    fun getStatsReport(): String = buildString {
        appendLine("Tool Usage Statistics:")
        appendLine()
        val sorted = stats.entries.sortedByDescending { it.value.callCount }
        for ((name, s) in sorted) {
            val avgMs = if (s.callCount > 0) s.totalDurationMs / s.callCount else 0
            val cacheRate = if (s.callCount > 0) (s.cacheHitCount * 100 / s.callCount) else 0
            appendLine("  $name: ${s.callCount} calls, ${s.errorCount} errors, ${cacheRate}% cache, avg ${avgMs}ms")
        }
    }

    fun checkMemory(toolName: String, argsHash: String): List<UIMessagePart>? {
        if (projectMemory == null || !projectMemory.hasProjectInfo()) return null
        if (toolName == "getProjectSummary") {
            val summary = projectMemory.getCachedSummary()
            if (summary.isNotBlank()) return listOf(UIMessagePart.Text(summary))
        }
        if (toolName == "getProjectStructure") {
            val structure = projectMemory.getCachedStructure()
            if (structure.isNotBlank()) return listOf(UIMessagePart.Text(structure))
        }
        return null
    }
}
