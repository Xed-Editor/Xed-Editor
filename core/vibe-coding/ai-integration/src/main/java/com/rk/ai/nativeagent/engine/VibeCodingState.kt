@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.engine

import com.rk.ai.agent.agents.AgentResult
import com.rk.ai.agent.events.SessionTodo
import com.rk.ai.agent.events.SessionTodoStatus
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

data class CommandCatalogEntry(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "custom",
    val slash: String = "",
    val prompt: String = "",
    val hidden: Boolean = false,
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
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(input)
    }

    fun toChatState(): ChatState = ChatState(messages, isProcessing, error, currentConversationId)
    fun toAgentActivityState(): AgentActivityState = AgentActivityState(agentActivities)
    fun toSecurityState(): SecurityState = SecurityState(securityAlerts, permissionAutoRespondRules)
    fun toSessionNavigationState(): SessionNavigationState = SessionNavigationState(sessionTree, activeSessionId, parentSessionId)
    fun toTaskState(): TaskState = TaskState(todos)
    fun toUIState(): UIState = UIState(commandCatalog, dockOpen, dockClosing, compactionReason)
}
