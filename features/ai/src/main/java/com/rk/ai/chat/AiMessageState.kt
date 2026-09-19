package com.rk.ai.chat

import com.rk.ai.model.AiChatMessage
import com.rk.ai.model.ToolCallStatus

/** Recursively marks a message and its sub-agent children as no longer streaming. */
internal fun AiChatMessage.stopped(): AiChatMessage =
    copy(isStreaming = false, children = children.map { it.stopped() })

/** True while a tool card should still show a spinner. */
internal val ToolCallStatus.isInProgress: Boolean
    get() =
        this == ToolCallStatus.Running ||
            this == ToolCallStatus.AwaitingApproval ||
            this == ToolCallStatus.AwaitingInput
