package com.rk.ai.models

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class ToolResult(
    val success: Boolean,
    val output: List<UIMessagePart> = emptyList(),
    val error: String? = null,
    val durationMs: Long = 0,
    val retryCount: Int = 0,
    val nextActions: List<String> = emptyList(),
    val toolHealth: ToolHealth = ToolHealth.UNKNOWN,
) {
    companion object {
        fun ok(text: String, durationMs: Long = 0): ToolResult = ToolResult(
            success = true,
            output = listOf(UIMessagePart.Text(text)),
            durationMs = durationMs,
            toolHealth = ToolHealth.HEALTHY,
        )

        fun ok(parts: List<UIMessagePart>, durationMs: Long = 0): ToolResult = ToolResult(
            success = true,
            output = parts,
            durationMs = durationMs,
            toolHealth = ToolHealth.HEALTHY,
        )

        fun fail(error: String, durationMs: Long = 0, retryable: Boolean = true): ToolResult = ToolResult(
            success = false,
            output = listOf(UIMessagePart.Text("Error: $error")),
            error = error,
            durationMs = durationMs,
            toolHealth = if (retryable) ToolHealth.DEGRADED else ToolHealth.UNHEALTHY,
            nextActions = if (retryable) listOf("retry") else emptyList(),
        )
    }

    fun toText(): String = output.joinToString("\n") { part ->
        when (part) {
            is UIMessagePart.Text -> part.text
            else -> part.toString()
        }
    }
}

@Serializable
enum class ToolHealth {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN,
}
