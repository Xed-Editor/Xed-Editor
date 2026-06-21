@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package com.rk.ai.agent

import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart
import com.rk.ai.core.MessageRole

object CompactionHandler {
    private const val PRUNE_MINIMUM = 20_000
    private const val PRUNE_PROTECT = 40_000
    private const val TOOL_OUTPUT_MAX_CHARS = 2_000
    private val PRUNE_PROTECTED_TOOLS = setOf("skill", "use_skill")
    private const val MIN_PRESERVE_RECENT_TOKENS = 2_000
    private const val MAX_PRESERVE_RECENT_TOKENS = 8_000
    private const val DEFAULT_TAIL_TURNS = 2

    data class CompactionResult(
        val compactedMessages: List<UIMessage>,
        val summary: String?,
        val prunedCount: Int,
    )

    fun needsCompaction(
        messages: List<UIMessage>,
        contextWindow: Int,
        maxOutputTokens: Int,
    ): Boolean {
        return TokenEstimator.isOverflow(messages, contextWindow, maxOutputTokens)
    }

    fun createCompactionPrompt(
        conversation: List<UIMessage>,
        previousSummary: String? = null,
    ): String {
        val anchor = if (previousSummary != null) {
            """
Update the anchored summary below using the conversation history above.
Preserve still-true details, remove stale details, and merge in the new facts.
<previous-summary>
$previousSummary
</previous-summary>
""".trimIndent()
        } else {
            "Create a new anchored summary from the conversation history above."
        }

        return """
$anchor

Output exactly the Markdown structure shown inside <template> and keep the section order unchanged. Do not include the <template> tags in your response.
<template>
## Goal
- [single-sentence task summary]

## Constraints & Preferences
- [user constraints, preferences, specs, or "(none)"]

## Progress
### Done
- [completed work or "(none)"]

### In Progress
- [current work or "(none)"]

### Blocked
- [blockers or "(none)"]

## Key Decisions
- [decision and why, or "(none)"]

## Next Steps
- [ordered next actions or "(none)"]

## Critical Context
- [important technical facts, errors, open questions, or "(none)"]

## Relevant Files
- [file or directory path: why it matters, or "(none)"]
</template>

Rules:
- Keep every section, even when empty.
- Use terse bullets, not prose paragraphs.
- Preserve exact file paths, commands, error strings, and identifiers when known.
- Do not mention the summary process or that context was compacted.

Conversation history to summarize:
${conversation.joinToString("\n") { m ->
    when (m.role) {
        MessageRole.USER -> "USER: ${m.toText()}"
        MessageRole.ASSISTANT -> "ASSISTANT: ${m.toText()}"
        MessageRole.SYSTEM -> "SYSTEM: ${m.toText()}"
        else -> "${m.role}: ${m.toText()}"
    }
}}
""".trimIndent()
    }

    fun pruneMessages(messages: List<UIMessage>): CompactionResult {
        if (messages.isEmpty()) return CompactionResult(messages, null, 0)

        val totalTokens = TokenEstimator.estimate(messages)
        if (totalTokens <= PRUNE_PROTECT) return CompactionResult(messages, null, 0)

        val budget = minOf(
            MAX_PRESERVE_RECENT_TOKENS,
            maxOf(MIN_PRESERVE_RECENT_TOKENS, totalTokens / 4)
        )

        val tailTurns = mutableListOf<UIMessage>()
        var tailTokens = 0
        for (msg in messages.reversed()) {
            val est = TokenEstimator.estimate(listOf(msg))
            if (tailTokens + est > budget && tailTurns.isNotEmpty()) break
            tailTokens += est
            tailTurns.add(msg)
        }
        tailTurns.reverse()

        if (tailTurns.isEmpty()) return CompactionResult(messages, null, 0)

        val tailStart = messages.indexOf(tailTurns.first())
        val headMessages = messages.subList(0, tailStart)

        var prunedCount = 0
        val compactedHead = headMessages.map { msg ->
            val tools = msg.getTools()
            if (tools.isEmpty()) return@map msg

            val hasLargeOutput = tools.any { tool ->
                val outputTokens = TokenEstimator.estimate(tool.output.joinToString("\n") { p ->
                    when (p) {
                        is UIMessagePart.Text -> p.text
                        else -> ""
                    }
                })
                outputTokens > TOOL_OUTPUT_MAX_CHARS / 4
            }
            if (!hasLargeOutput) return@map msg

            prunedCount++
            val updatedParts = msg.parts.map { part ->
                if (part is UIMessagePart.Tool && part.isExecuted) {
                    val truncatedOutput = part.output.map { p ->
                        when (p) {
                            is UIMessagePart.Text -> UIMessagePart.Text(p.text.take(TOOL_OUTPUT_MAX_CHARS) + "\n... [truncated]")
                            else -> p
                        }
                    }
                    part.copy(output = truncatedOutput)
                } else part
            }
            msg.copy(parts = updatedParts)
        }

        return CompactionResult(
            compactedMessages = compactedHead + tailTurns,
            summary = null,
            prunedCount = prunedCount,
        )
    }

    fun detectDoomLoop(
        messages: List<UIMessage>,
        threshold: Int = 3,
    ): String? {
        if (messages.isEmpty()) return null

        val allToolCalls = messages.flatMap { msg ->
            msg.getTools().filter { it.isExecuted }
        }

        val recentToolCalls = allToolCalls.takeLast(threshold)
        if (recentToolCalls.size < threshold) return null

        val toolEntry = mutableListOf<Pair<String, String>>()
        for (toolResult in recentToolCalls) {
            val key = toolResult.toolName to toolResult.input.take(200)
            toolEntry.add(key)
        }

        val allSameName = toolEntry.all { it.first == toolEntry.first().first }
        val allSameInput = toolEntry.all { it.second == toolEntry.first().second }
        if (allSameName && allSameInput) {
            return toolEntry.first().first
        }

        return null
    }
}
