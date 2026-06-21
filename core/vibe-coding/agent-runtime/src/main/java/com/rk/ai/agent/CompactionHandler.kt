@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package com.rk.ai.agent

import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart
import com.rk.ai.core.MessageRole

object CompactionHandler {
    private const val PRUNE_MINIMUM = 20_000
    private const val PRUNE_PROTECT = 40_000
    private const val TOOL_OUTPUT_MAX_CHARS = 2_000
    private val PRUNE_PROTECTED_TOOLS = setOf("skill", "use_skill", "memory_tool")
    private const val MIN_PRESERVE_RECENT_TOKENS = 2_000
    private const val MAX_PRESERVE_RECENT_TOKENS = 8_000
    private const val DEFAULT_TAIL_TURNS = 2
    private const val DOOM_LOOP_THRESHOLD = 3
    private const val PATTERN_WINDOW = 6
    private const val PATTERN_REPEAT_THRESHOLD = 2

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
- Preserve build errors, compiler messages, and test failures verbatim.
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
                val outputText = tool.output.joinToString("\n") { p ->
                    when (p) {
                        is UIMessagePart.Text -> p.text
                        else -> ""
                    }
                }
                val outputTokens = TokenEstimator.estimate(outputText)
                outputTokens > TOOL_OUTPUT_MAX_CHARS / 4
            }
            if (!hasLargeOutput) return@map msg

            prunedCount++
            val updatedParts = msg.parts.map { part ->
                if (part is UIMessagePart.Tool && part.isExecuted) {
                    val truncatedOutput = part.output.map { p ->
                        when (p) {
                            is UIMessagePart.Text -> {
                                val text = p.text
                                if (text.length > TOOL_OUTPUT_MAX_CHARS) {
                                    val (summary, keyLines) = smartTruncate(text, TOOL_OUTPUT_MAX_CHARS)
                                    UIMessagePart.Text(summary + "\n... [truncated]" + keyLines)
                                } else p
                            }
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

    /**
     * Smart truncation that preserves the first portion, key diagnostic lines,
     * and error messages while cutting verbose middle content.
     */
    private fun smartTruncate(text: String, maxChars: Int): Pair<String, String> {
        val lines = text.lines()
        if (lines.size <= 3) return text.take(maxChars) to ""

        val keyLines = mutableListOf<String>()
        val midStart = (lines.size * 0.1).toInt().coerceAtLeast(1)
        val midEnd = (lines.size * 0.9).toInt().coerceAtMost(lines.size - 1)

        // Preserve error lines from the middle
        for (i in midStart until midEnd) {
            val line = lines[i]
            if (line.contains("error", ignoreCase = true) ||
                line.contains("exception", ignoreCase = true) ||
                line.contains("FAILED", ignoreCase = true) ||
                line.contains("warning:", ignoreCase = true) ||
                line.matches(Regex("^\\s*(at |Caused by|... \\d+ more)"))
            ) {
                keyLines.add(line)
            }
        }

        val head = lines.take(midStart).joinToString("\n").take(maxChars)
        val tailSuffix = if (keyLines.isNotEmpty()) {
            "\n\nKey diagnostics from truncated section:\n" + keyLines.distinct().take(10).joinToString("\n")
        } else ""

        return head to tailSuffix
    }

    fun detectDoomLoop(
        messages: List<UIMessage>,
        threshold: Int = DOOM_LOOP_THRESHOLD,
    ): String? {
        if (messages.isEmpty()) return null

        val recentToolCalls = messages.flatMap { msg ->
            msg.getTools().filter { it.isExecuted }
        }.takeLast(threshold)

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

    fun detectPatternLoop(
        recentToolNameSequences: List<List<String>>,
    ): Boolean {
        if (recentToolNameSequences.size < 4) return false
        val half = recentToolNameSequences.size / 2
        val firstHalf = recentToolNameSequences.take(half).flatten()
        val secondHalf = recentToolNameSequences.drop(half).flatten()
        return firstHalf == secondHalf && firstHalf.isNotEmpty()
    }

    fun detectExcessiveReads(
        messages: List<UIMessage>,
        readTools: Set<String> = setOf("readFile", "cat", "readFiles", "head", "getFileContent"),
        maxReadsPerWindow: Int = 30,
        windowSize: Int = 30,
    ): Boolean {
        val recentMessages = messages.takeLast(windowSize)
        val readTargets = mutableSetOf<String>()
        var readCount = 0
        for (msg in recentMessages) {
            for (tool in msg.getTools().filter { it.toolName in readTools && it.isExecuted }) {
                readCount++
                val path = extractFilePath(tool.input)
                if (path != null) readTargets.add(path)
            }
        }
        if (readCount <= maxReadsPerWindow) return false
        if (readTargets.size >= readCount / 2) return false
        return true
    }

    private fun extractFilePath(input: String): String? {
        val patterns = listOf(
            """"filePath"\s*:\s*"([^"]+)"""",
            """"path"\s*:\s*"([^"]+)"""",
            """"file"\s*:\s*"([^"]+)"""",
        )
        for (pattern in patterns) {
            val match = Regex(pattern).find(input)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    fun buildRecoveryMessage(
        loopType: String,
        toolName: String,
        details: String = "",
    ): UIMessage {
        val message = when (loopType) {
            "doom_loop" -> """
[SYSTEM: The tool '$toolName' has been called repeatedly with the same arguments.
This appears to be a loop. Try a fundamentally different approach:
- Read the relevant file first to see what's actually there
- Check if you already have the information you need
- Use a different tool or strategy entirely
- If you're stuck, call getGuidelines for help]

[RECOMMENDATION: $details]
""".trimIndent()

            "pattern_loop" -> """
[SYSTEM: The agent appears to be stuck in a loop calling the same tools repeatedly.
Stop calling these tools and provide a summary of what you have found so far.
If you need more information, try a completely different approach or ask the user for guidance.]

[RECOMMENDATION: $details]
""".trimIndent()

            "excessive_reads" -> """
[SYSTEM: You have performed many file read operations recently.
Before reading more files, check if you already have the information you need.
Focus on synthesizing what you know and making progress toward the goal.]

[RECOMMENDATION: $details]
""".trimIndent()

            else -> """
[SYSTEM: The agent appears stuck. Try a different approach.
Call getGuidelines for a refresher on available tools and strategies.]

[RECOMMENDATION: $details]
""".trimIndent()
        }
        return UIMessage(
            role = MessageRole.SYSTEM,
            parts = listOf(UIMessagePart.Text(message)),
        )
    }
}
