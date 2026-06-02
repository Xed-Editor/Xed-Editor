package com.rk.ai.agent

import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart

object TokenEstimator {
    private const val CHARS_PER_TOKEN = 4.0

    fun estimate(text: String): Int {
        return (text.length / CHARS_PER_TOKEN).toInt().coerceAtLeast(1)
    }

    fun estimate(messages: List<UIMessage>): Int {
        return messages.sumOf { msg ->
            estimate(msg.toText()) + msg.parts.sumOf { part ->
                when (part) {
                    is UIMessagePart.Image -> 500
                    is UIMessagePart.Document -> 300
                    is UIMessagePart.Video -> 2000
                    is UIMessagePart.Audio -> 1000
                    else -> 0
                }
            }
        }
    }

    fun usableTokens(contextWindow: Int, maxOutputTokens: Int, reserved: Int = 20_000): Int {
        val actualReserved = minOf(reserved, maxOutputTokens.coerceAtLeast(1))
        return (contextWindow - actualReserved).coerceAtLeast(1)
    }

    fun isOverflow(messages: List<UIMessage>, contextWindow: Int, maxOutputTokens: Int): Boolean {
        val estimated = estimate(messages)
        val usable = usableTokens(contextWindow, maxOutputTokens)
        return estimated >= usable
    }

    fun truncateByTokens(messages: List<UIMessage>, maxTokens: Int): List<UIMessage> {
        if (maxTokens <= 0 || messages.isEmpty()) return messages
        var total = 0
        val result = mutableListOf<UIMessage>()
        for (msg in messages.reversed()) {
            val size = estimate(listOf(msg))
            if (total + size > maxTokens && result.isNotEmpty()) break
            total += size
            result.add(msg)
            if (total >= maxTokens) break
        }
        return result.reversed()
    }
}
