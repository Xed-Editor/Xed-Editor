@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.engine

import com.rk.ai.agent.agents.AgentResult
import com.rk.ai.agent.events.SessionTodo
import com.rk.ai.agent.events.SessionTodoStatus
import com.rk.ai.agent.executor.AgentPhase
import com.rk.ai.agent.planner.TaskTree
import com.rk.ai.models.UIMessage
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

data class AgentActivity(
    val agentName: String,
    val task: String,
    val status: AgentActivityStatus,
    val result: AgentResult? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

enum class AgentActivityStatus {
    PENDING, RUNNING, COMPLETED, FAILED
}

data class SecurityAlert(
    val id: String? = null,
    val severity: String,
    val message: String,
    val toolName: String? = null,
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

data class SessionNode(
    val id: Uuid,
    val parentId: Uuid? = null,
    val title: String = "New Session",
    val createdAt: Long = System.currentTimeMillis(),
    val messages: List<UIMessage> = emptyList(),
    val isArchived: Boolean = false,
)

data class PermissionAutoRespondRule(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val toolPattern: String,
    val argPattern: String = "*",
    val action: PermissionAction,
    val description: String = "",
)

enum class PermissionAction { ALLOW, ASK, DENY }

data class DebugInfo(
    val lastPrompt: String = "",
    val lastResponse: String = "",
    val lastToolCalls: List<String> = emptyList(),
    val inputMessages: List<UIMessage> = emptyList(),
    val outputMessages: List<UIMessage> = emptyList(),
    val modelName: String = "",
    val totalTokens: Int = 0,
)

data class CommandCatalogEntry(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "custom",
    val slash: String = "",
    val prompt: String = "",
    val hidden: Boolean = false,
)

data class ToolExecutionRecord(
    val toolName: String,
    val durationMs: Long,
    val success: Boolean,
    val fromCache: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val tokens: Int = 0,
)

@OptIn(ExperimentalUuidApi::class)
data class VibeCodingState(
    val messages: List<UIMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val currentConversationId: Uuid? = null,
    val agentActivities: List<AgentActivity> = emptyList(),
    val securityAlerts: List<SecurityAlert> = emptyList(),
    val sessionTree: List<SessionNode> = emptyList(),
    val activeSessionId: Uuid? = null,
    val parentSessionId: Uuid? = null,
    val todos: List<SessionTodo> = emptyList(),
    val permissionAutoRespondRules: List<PermissionAutoRespondRule> = emptyList(),
    val commandCatalog: List<CommandCatalogEntry> = emptyList(),
    val dockOpen: Boolean = false,
    val dockClosing: Boolean = false,
    val compactionReason: String? = null,
    val currentPhase: AgentPhase = AgentPhase.IDLE,
    val contextTokens: Int? = null,
    val toolExecutions: List<ToolExecutionRecord> = emptyList(),
    val taskTree: TaskTree? = null,
    val modifiedFiles: List<String> = emptyList(),
    val projectIndexed: Boolean = false,
    val toolStatsSummary: String = "",
    val debugMode: Boolean = false,
    val debugInfo: DebugInfo? = null,
) {
    val sessionById: Map<Uuid, SessionNode> get() = sessionTree.associateBy { it.id }
    val hasSecurityAlerts: Boolean get() = securityAlerts.isNotEmpty()
    val activeAgents: List<AgentActivity> get() = agentActivities.filter {
        it.status == AgentActivityStatus.RUNNING || it.status == AgentActivityStatus.PENDING
    }

    val currentSessionNode: SessionNode? get() {
        val id = activeSessionId ?: return null
        return sessionById[id]
    }

    val hasParentSession: Boolean get() = parentSessionId != null

    val completedTodos: Int get() = todos.count { it.status == SessionTodoStatus.COMPLETED }
    val pendingTodos: Int get() = todos.count { it.status == SessionTodoStatus.PENDING }

    val phaseLabel: String get() = when (currentPhase) {
        AgentPhase.IDLE -> "Idle"
        AgentPhase.PLANNING -> "Planning"
        AgentPhase.ANALYZING -> "Analyzing"
        AgentPhase.INDEXING -> "Indexing"
        AgentPhase.EXPLORING -> "Exploring"
        AgentPhase.EXECUTING -> "Executing"
        AgentPhase.VERIFYING -> "Verifying"
        AgentPhase.REVIEWING -> "Reviewing"
        AgentPhase.TESTING -> "Testing"
        AgentPhase.COMPLETED -> "Completed"
        AgentPhase.FAILED -> "Failed"
    }

    val isAgentActive: Boolean get() = currentPhase !in listOf(AgentPhase.IDLE, AgentPhase.COMPLETED, AgentPhase.FAILED)

    val phaseColor: Long get() = when (currentPhase) {
        AgentPhase.IDLE -> 0xFF9E9E9E
        AgentPhase.PLANNING -> 0xFFFFA726
        AgentPhase.ANALYZING -> 0xFF42A5F5
        AgentPhase.INDEXING -> 0xFF66BB6A
        AgentPhase.EXPLORING -> 0xFF26C6DA
        AgentPhase.EXECUTING -> 0xFFEF5350
        AgentPhase.VERIFYING -> 0xFFAB47BC
        AgentPhase.REVIEWING -> 0xFFFF7043
        AgentPhase.TESTING -> 0xFF7E57C2
        AgentPhase.COMPLETED -> 0xFF66BB6A
        AgentPhase.FAILED -> 0xFFEF5350
    }

    fun sessionLineage(sessionId: Uuid): List<Uuid> {
        val result = mutableListOf(sessionId)
        var current = sessionById[sessionId]
        while (current?.parentId != null) {
            result.add(current.parentId!!)
            current = sessionById[current.parentId]
        }
        return result
    }

    fun shouldAutoRespondPermission(toolName: String, inputJson: String): PermissionAction? {
        for (rule in permissionAutoRespondRules.reversed()) {
            if (patternMatches(rule.toolPattern, toolName) && patternMatches(rule.argPattern, inputJson)) {
                return rule.action
            }
        }
        return null
    }

    fun isToolMatchedByRule(rule: PermissionAutoRespondRule, toolName: String): Boolean {
        return patternMatches(rule.toolPattern, toolName)
    }

    private fun patternMatches(pattern: String, input: String): Boolean {
        return com.rk.ai.nativeagent.engine.patternMatches(pattern, input)
    }

    fun toChatState(): ChatState = ChatState(messages, isProcessing, error, currentConversationId)
    fun toAgentActivityState(): AgentActivityState = AgentActivityState(agentActivities)
    fun toSecurityState(): SecurityState = SecurityState(securityAlerts, permissionAutoRespondRules)
    fun toSessionNavigationState(): SessionNavigationState = SessionNavigationState(sessionTree, activeSessionId, parentSessionId)
    fun toTaskState(): TaskState = TaskState(todos)
    fun toUIState(): UIState = UIState(commandCatalog, dockOpen, dockClosing, compactionReason)

    fun exportAsMarkdown(): String = buildString {
        appendLine("# VibeCoding Conversation")
        appendLine()
        for (msg in messages) {
            val role = when (msg.role) {
                com.rk.ai.core.MessageRole.USER -> "**User**"
                com.rk.ai.core.MessageRole.ASSISTANT -> "**Assistant**"
                com.rk.ai.core.MessageRole.SYSTEM -> "*System*"
                else -> "**${msg.role.name}**"
            }
            appendLine("### $role")
            appendLine()
            val text = msg.toText()
            if (text.isNotBlank()) {
                appendLine(text)
                appendLine()
            }
            val tools = msg.getTools()
            if (tools.isNotEmpty()) {
                for (tool in tools) {
                    appendLine("> Tool: `${tool.toolName}` — ${tool.statusLabel}")
                    if (tool.output.isNotEmpty()) {
                        appendLine("> ```")
                        tool.output.forEach { part ->
                            if (part is com.rk.ai.models.UIMessagePart.Text) {
                                appendLine("> ${part.text.take(200)}")
                            }
                        }
                        appendLine("> ```")
                    }
                }
                appendLine()
            }
        }
        appendLine("---")
        appendLine("*Exported from Xed-Editor VibeCoding*")
    }
}
