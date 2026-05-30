package com.rk.ai.nativeagent.engine

import com.rk.ai.models.UIMessage

data class VibeCodingState(
    val messages: List<UIMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
)
