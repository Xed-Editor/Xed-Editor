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

        val msgsWithTokens = messages.map { msg ->
            msg to TokenEstimator.estimate(msg.toText())
        }
        val totalTokens = msgsWithTokens.sumOf { it.second }
        if (totalTokens <= PRUNE_PROTECT) return CompactionResult(messages, null, 0)

        var prunedTokens = 0
        var prunedCount = 0
        val pruned = messages.toMutableList()

        var userTurns = 0
        for (i in pruned.lastIndex downTo 0) {
            if (pruned[i].role == MessageRole.USER) userTurns++
            if (userTurns < 2) continue

            val tools = pruned[i].getTools()
            val executableTools = tools.filter { it.isExecuted }
            for (tool in executableTools) {
                val shortOutput = tool.output.joinToString("\n") { p ->
                    when (p) {
                        is UIMessagePart.Text -> p.text.take(TOOL_OUTPUT_MAX_CHARS)
                        else -> ""
                    }
                }
                val estimate = TokenEstimator.estimate(shortOutput)
                if (totalTokens - prunedTokens <= PRUNE_PROTECT) break
                prunedTokens += estimate
                prunedCount++
            }
            if (totalTokens - prunedTokens <= PRUNE_PROTECT) break
        }

        if (prunedTokens < PRUNE_MINIMUM) {
            return CompactionResult(messages, null, 0)
        }

        val budget = minOf(
            MAX_PRESERVE_RECENT_TOKENS,
            maxOf(MIN_PRESERVE_RECENT_TOKENS, TokenEstimator.estimate(messages) / 4)
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

        val headMessages = if (tailTurns.isNotEmpty()) {
            val tailStart = messages.indexOf(tailTurns.first())
            messages.subList(0, tailStart)
        } else {
            messages
        }

        return CompactionResult(
            compactedMessages = headMessages,
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
