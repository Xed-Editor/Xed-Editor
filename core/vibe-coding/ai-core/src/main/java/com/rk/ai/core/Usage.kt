package com.rk.ai.core

import kotlinx.serialization.Serializable

@Serializable
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
    val totalTokens: Int = 0,
)

fun TokenUsage?.merge(other: TokenUsage): TokenUsage {
    val promptTokens = other.promptTokens.takeIf { it > 0 } ?: (this?.promptTokens ?: 0)
    val completionTokens = other.completionTokens.takeIf { it > 0 } ?: (this?.completionTokens ?: 0)
    val cachedTokens = other.cachedTokens.takeIf { it > 0 } ?: (this?.cachedTokens ?: 0)
    return TokenUsage(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = promptTokens + completionTokens,
        cachedTokens = cachedTokens
    )
}
