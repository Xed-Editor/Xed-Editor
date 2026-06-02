package com.rk.ai.nativeagent.engine

import com.rk.ai.agent.agents.AgentResult
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

@OptIn(ExperimentalUuidApi::class)
data class VibeCodingState(
    val messages: List<UIMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val currentConversationId: Uuid? = null,
    val agentActivities: List<AgentActivity> = emptyList(),
    val securityAlerts: List<SecurityAlert> = emptyList(),
) {
    val hasSecurityAlerts: Boolean get() = securityAlerts.isNotEmpty()
    val activeAgents: List<AgentActivity> get() = agentActivities.filter {
        it.status == AgentActivityStatus.RUNNING || it.status == AgentActivityStatus.PENDING
    }
}
