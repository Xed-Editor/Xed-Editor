package com.rk.ai.coding

import com.rk.ai.coding.context.ContextBuildOptions
import com.rk.ai.coding.context.WorkspaceContext
import com.rk.ai.coding.tools.ToolApprovalRequest
import com.rk.ai.models.Assistant
import com.rk.ai.models.UIMessage
import com.rk.ai.persistence.settings.Settings
import com.rk.ai.providers.Model

data class CodingAgentRequest(
    val sessionId: String? = null,
    val userText: String = "",
    val settings: Settings,
    val model: Model? = null,
    val assistant: Assistant? = null,
    val history: List<UIMessage> = emptyList(),
    val maxSteps: Int = 64,
    val resumePendingTools: Boolean = false,
    val contextOptions: ContextBuildOptions = ContextBuildOptions(),
)

enum class CodingAgentSessionStatus {
    Idle,
    Running,
    WaitingForApproval,
    Completed,
    Cancelled,
    Failed,
}

data class CodingAgentSessionSnapshot(
    val id: String,
    val status: CodingAgentSessionStatus,
    val messages: List<UIMessage>,
    val createdAt: Long,
    val updatedAt: Long,
)

sealed class CodingAgentEvent {
    data class SessionStarted(val sessionId: String) : CodingAgentEvent()
    data class ContextBuilt(val sessionId: String, val context: WorkspaceContext) : CodingAgentEvent()
    data class Messages(
        val sessionId: String,
        val messages: List<UIMessage>,
        val pendingApprovals: List<ToolApprovalRequest>,
    ) : CodingAgentEvent()
    data class Completed(val sessionId: String, val messages: List<UIMessage>) : CodingAgentEvent()
    data class Cancelled(val sessionId: String) : CodingAgentEvent()
    data class Error(val sessionId: String, val throwable: Throwable) : CodingAgentEvent()
}
