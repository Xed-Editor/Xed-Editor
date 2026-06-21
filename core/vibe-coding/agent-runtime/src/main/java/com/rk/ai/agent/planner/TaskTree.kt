@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.agent.planner

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class TaskStatus { PENDING, IN_PROGRESS, COMPLETED, BLOCKED, FAILED, SKIPPED }

enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

data class TaskNode(
    val id: String = Uuid.random().toString(),
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val subtasks: List<TaskNode> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    val result: String? = null,
    val error: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val estimatedFiles: Int = 1,
    val requiresBuild: Boolean = false,
    val requiresTest: Boolean = false,
)

data class TaskTree(
    val id: String = Uuid.random().toString(),
    val goal: String,
    val rootTasks: List<TaskNode>,
    val riskLevel: RiskLevel = RiskLevel.LOW,
) {
    fun flatten(): List<TaskNode> {
        fun flattenNode(node: TaskNode): List<TaskNode> = listOf(node) + node.subtasks.flatMap { flattenNode(it) }
        return rootTasks.flatMap { flattenNode(it) }
    }

    fun nextExecutable(): TaskNode? {
        val all = flatten()
        val completedIds = all.filter { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.SKIPPED }.map { it.id }.toSet()
        return all.firstOrNull { task ->
            task.status == TaskStatus.PENDING && task.dependencies.all { it in completedIds }
        }
    }

    val totalCount: Int get() = flatten().size
    val completedCount: Int get() = flatten().count { it.status == TaskStatus.COMPLETED }
    val failedCount: Int get() = flatten().count { it.status == TaskStatus.FAILED }
    val progress: Float get() = if (totalCount == 0) 1f else completedCount.toFloat() / totalCount
    val isComplete: Boolean get() = flatten().all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.SKIPPED || it.status == TaskStatus.FAILED }

    fun updateTask(id: String, transform: TaskNode.() -> TaskNode): TaskTree {
        fun updateIn(node: TaskNode): TaskNode {
            if (node.id == id) return node.transform()
            return node.copy(subtasks = node.subtasks.map { updateIn(it) })
        }
        return copy(rootTasks = rootTasks.map { updateIn(it) })
    }

    fun toSummary(): String = buildString {
        appendLine("## Execution Plan: $goal")
        appendLine("Progress: $completedCount/$totalCount (${(progress * 100).toInt()}%)")
        appendLine("Risk Level: $riskLevel")
        appendLine()
        fun printNode(node: TaskNode, indent: Int) {
            val prefix = when (node.status) {
                TaskStatus.COMPLETED -> "  [✓]"
                TaskStatus.IN_PROGRESS -> "  [→]"
                TaskStatus.FAILED -> "  [✗]"
                TaskStatus.BLOCKED -> "  [⏸]"
                TaskStatus.SKIPPED -> "  [–]"
                TaskStatus.PENDING -> "  [ ]"
            }
            val indentStr = "  ".repeat(indent)
            val riskTag = if (node.riskLevel.ordinal >= RiskLevel.HIGH.ordinal) " [!${node.riskLevel}]" else ""
            val filesTag = if (node.estimatedFiles > 1) " (~${node.estimatedFiles} files)" else ""
            appendLine("$indentStr$prefix ${node.title}$riskTag$filesTag")
            if (node.error != null) appendLine("$indentStr      Error: ${node.error}")
            node.subtasks.forEach { printNode(it, indent + 1) }
        }
        rootTasks.forEach { printNode(it, 0) }
    }
}
