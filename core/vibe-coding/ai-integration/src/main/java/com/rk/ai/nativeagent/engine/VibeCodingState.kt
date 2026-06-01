package com.rk.ai.nativeagent.engine

import com.rk.ai.models.UIMessage
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class VibeCodingState(
    val messages: List<UIMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val currentConversationId: Uuid? = null,
)
