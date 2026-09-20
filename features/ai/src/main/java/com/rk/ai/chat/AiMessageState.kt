package com.rk.ai.chat

import com.rk.ai.model.AiChatMessage
import com.rk.ai.model.ToolCallStatus

internal fun AiChatMessage.stopped(): AiChatMessage =
    copy(isStreaming = false, children = children.map { it.stopped() })

internal val ToolCallStatus.isInProgress: Boolean
    get() =
        this == ToolCallStatus.Running ||
            this == ToolCallStatus.AwaitingApproval ||
            this == ToolCallStatus.AwaitingInput
