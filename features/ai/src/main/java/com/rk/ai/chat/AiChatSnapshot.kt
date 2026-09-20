package com.rk.ai.chat

import com.rk.ai.model.AiChatMessage
import com.rk.ai.model.AiTodo
import com.rk.ai.model.AiTurn
import com.rk.ai.model.ToolCallStatus

/**
 * Everything one AI chat tab is made of, in the form the tab persists and restores.
 *
 * The type is deliberately flat and made of the same immutable models the controller already holds,
 * so encoding a screenshot of a chat is a matter of handing over the collections rather than mapping
 * them into a parallel representation.
 *
 * The snapshot says nothing about *when* it was taken: the controller keeps its own change counter
 * next to the encoded form and only rebuilds this when that counter moved, so a save with no changes
 * costs a comparison rather than a walk over the transcript.
 */
data class AiChatSnapshot(
    val messages: List<AiChatMessage> = emptyList(),
    val history: List<AiTurn> = emptyList(),
    val goal: String? = null,
    val todos: List<AiTodo> = emptyList(),
    val draft: String = "",
    val nextId: Long = 0L,
    /** Whether the model's reasoning is folded out in the transcript. */
    val showReasoning: Boolean = false,
) {
    /**
     * The snapshot as it should be restored, with in-flight work settled.
     *
     * A tool call can only be running while a run is, and a run never survives the process, so any
     * status that still promises progress is turned into [ToolCallStatus.Interrupted] here rather
     * than left for the UI to keep spinning on forever.
     */
    fun settled(): AiChatSnapshot = copy(messages = messages.map { it.settled() })
}

private fun AiChatMessage.settled(): AiChatMessage =
    copy(
        isStreaming = false,
        toolStatus = if (toolStatus?.isInProgress == true) ToolCallStatus.Interrupted else toolStatus,
        children = children.map { it.settled() },
    )
