package com.rk.ai.agent.review

data class ActionRecord(
    val toolName: String,
    val inputHash: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val outputHash: Int? = null,
)

data class LoopInfo(
    val pattern: String,
    val description: String,
    val severity: LoopSeverity,
    val suggestion: String,
)

enum class LoopSeverity { WARNING, CRITICAL }

class InfiniteLoopDetector(
    private val windowSize: Int = 12,
    private val repeatThreshold: Int = 3,
) {
    private val actionHistory = mutableListOf<ActionRecord>()

    fun record(action: ActionRecord) {
        actionHistory.add(action)
        while (actionHistory.size > windowSize * 3) {
            actionHistory.removeAt(0)
        }
    }

    fun detect(): LoopInfo? {
        if (actionHistory.size < 4) return null

        val exactRepeat = detectExactRepeat()
        if (exactRepeat != null) return exactRepeat

        val patternRepeat = detectPatternRepeat()
        if (patternRepeat != null) return patternRepeat

        val oscillating = detectOscillation()
        if (oscillating != null) return oscillating

        val excessiveReads = detectExcessiveProjectReads()
        if (excessiveReads != null) return excessiveReads

        return null
    }

    private fun detectExactRepeat(): LoopInfo? {
        val recent = actionHistory.takeLast(windowSize)
        val toolGroups = recent.groupBy { it.toolName }
        for ((toolName, calls) in toolGroups) {
            if (calls.size >= repeatThreshold) {
                val inputHashes = calls.map { it.inputHash }.distinct()
                if (inputHashes.size == 1) {
                    return LoopInfo(
                        pattern = toolName,
                        description = "Tool '$toolName' called ${calls.size}x with same input",
                        severity = LoopSeverity.CRITICAL,
                        suggestion = "Stop calling '$toolName' with the same arguments. Use cached information or try a different approach.",
                    )
                }
            }
        }
        return null
    }

    private fun detectPatternRepeat(): LoopInfo? {
        val recent = actionHistory.takeLast(windowSize)
        if (recent.size < 6) return null

        for (patternLen in 2..4) {
            if (recent.size >= patternLen * 2) {
                val first = recent.take(patternLen).map { it.toolName }
                val second = recent.drop(patternLen).take(patternLen).map { it.toolName }
                if (first == second) {
                    return LoopInfo(
                        pattern = first.joinToString("->"),
                        description = "Tool pattern repeated: ${first.joinToString(" → ")}",
                        severity = LoopSeverity.WARNING,
                        suggestion = "You're in a loop calling: ${first.joinToString(", ")}. Try a different strategy.",
                    )
                }
            }
        }
        return null
    }

    private fun detectOscillation(): LoopInfo? {
        val recent = actionHistory.takeLast(6).map { it.toolName }
        if (recent.size < 4) return null
        if (recent[0] == recent[2] && recent[1] == recent[3]) {
            return LoopInfo(
                pattern = "${recent[0]}↔${recent[1]}",
                description = "Oscillating between '${recent[0]}' and '${recent[1]}'",
                severity = LoopSeverity.WARNING,
                suggestion = "You're alternating between '${recent[0]}' and '${recent[1]}'. Try calling a different tool or reasoning from the data you already have.",
            )
        }
        return null
    }

    private fun detectExcessiveProjectReads(): LoopInfo? {
        val projectTools = setOf("getProjectStructure", "getProjectSummary", "getProjectConfig", "listFiles", "ls", "indexCodebase")
        val recent = actionHistory.takeLast(windowSize)
        val projectCalls = recent.filter { it.toolName in projectTools }
        if (projectCalls.size >= 4) {
            return LoopInfo(
                pattern = projectCalls.joinToString(", ") { it.toolName },
                description = "Project structure read ${projectCalls.size}x in last ${windowSize} calls",
                severity = LoopSeverity.WARNING,
                suggestion = "You already have the project structure. Use it instead of calling these tools again.",
            )
        }
        return null
    }

    fun clear() {
        actionHistory.clear()
    }
}
