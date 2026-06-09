@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.engine

import com.rk.ai.models.UIMessage
import kotlin.uuid.Uuid

data class ChatState(
    val messages: List<UIMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val currentConversationId: Uuid? = null,
)
