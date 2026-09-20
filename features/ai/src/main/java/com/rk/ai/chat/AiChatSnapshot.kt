package com.rk.ai.chat

import com.rk.ai.model.AiChatMessage
import com.rk.ai.model.AiTodo
import com.rk.ai.model.AiTurn
import com.rk.ai.model.ToolCallStatus

data class AiChatSnapshot(
    val messages: List<AiChatMessage> = emptyList(),
    val history: List<AiTurn> = emptyList(),
    val goal: String? = null,
    val todos: List<AiTodo> = emptyList(),
    val draft: String = "",
    val nextId: Long = 0L,
    val showReasoning: Boolean = false,
) {
    /**
     * The snapshot as it should be restored, with in-flight work settled: a run never survives the
     * process, so any status still promising progress becomes [ToolCallStatus.Interrupted].
     */
    fun settled(): AiChatSnapshot = copy(messages = messages.map { it.settled() })
}

private fun AiChatMessage.settled(): AiChatMessage =
    copy(
        isStreaming = false,
        toolStatus = if (toolStatus?.isInProgress == true) ToolCallStatus.Interrupted else toolStatus,
        children = children.map { it.settled() },
    )
