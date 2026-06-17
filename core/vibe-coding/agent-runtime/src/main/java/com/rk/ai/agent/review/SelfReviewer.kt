package com.rk.ai.agent.review

import android.util.Log
import com.rk.ai.agent.context.ContextBundle
import com.rk.ai.models.ExecutionState
import com.rk.ai.models.UIMessagePart

private const val TAG = "SelfReviewer"

data class ReviewReport(
    val passed: Boolean,
    val score: Int = 100,
    val feedback: String = "",
    val suggestions: List<String> = emptyList(),
    val missingInfo: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

class SelfReviewer {

    fun reviewToolResults(
        toolName: String,
        toolInput: String,
        result: List<UIMessagePart>,
        executionState: ExecutionState,
        context: ContextBundle?,
    ): ReviewReport {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        val missingInfo = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (executionState is ExecutionState.Error) {
            issues.add("Tool '$toolName' failed: ${executionState.error}")
            val suggestion = generateSuggestion(toolName, executionState.error)
            if (suggestion != null) suggestions.add(suggestion)
            return ReviewReport(
                passed = false,
                score = 0,
                feedback = issues.joinToString("\n"),
                suggestions = suggestions,
                missingInfo = listOf("Tool execution error"),
            )
        }

        val outputText = result.joinToString("\n") { part ->
            if (part is UIMessagePart.Text) part.text else ""
        }

        if (outputText.isBlank() || outputText == "null" || outputText == "[]") {
            issues.add("Tool returned empty result")
            suggestions.add("Tool '$toolName' returned no output - check if the input was valid")
        }

        if (executionState is ExecutionState.Completed) {
            if (toolName == "getFileContent" && outputText.contains("null")) {
                issues.add("File content is null - file may not exist")
                suggestions.add("Verify the file path exists and try alternative paths")
            }
            if (toolName == "writeFile" && outputText.length < 10) {
                issues.add("Write result too short - possible failure")
                suggestions.add("Check writeFile output for error messages")
            }
            if (toolName == "listFiles" || toolName == "ls") {
                if (outputText == "[]" || outputText.isBlank()) {
                    issues.add("Directory appears empty - may not exist")
                    suggestions.add("Check the parent directory exists and try expanding the path")
                }
            }
            if (toolName == "readFile" || toolName == "getFileContent") {
                if (outputText.length < 100 && !outputText.contains("error", ignoreCase = true)) {
                    missingInfo.add("Content seems truncated or unexpectedly short")
                    suggestions.add("File may be empty or not fully read - consider specifying line range")
                }
            }

            if (toolName == "searchCode" || toolName == "grep" || toolName == "searchSymbols") {
                if (outputText.contains("No results") || outputText.startsWith("No ")) {
                    issues.add("Search returned no results")
                    suggestions.add("Try different search terms or check file extensions")
                }
            }
        }

        return ReviewReport(
            passed = issues.isEmpty(),
            score = if (issues.isEmpty()) 100 else 30,
            feedback = issues.joinToString("\n"),
            suggestions = suggestions,
            missingInfo = issues,
        )
    }

    fun shouldRetry(report: ReviewReport, attempt: Int, maxAttempts: Int): Boolean {
        if (attempt >= maxAttempts) return false
        if (report.score >= 90) return false
        if (report.warnings.size <= 2 && report.missingInfo.isEmpty()) return false
        return true
    }

    private fun generateSuggestion(toolName: String, error: String): String? {
        return when {
            error.contains("not found", ignoreCase = true) -> "Verify the path exists and try listing the parent directory"
            error.contains("permission", ignoreCase = true) -> "Check file permissions or try a different location"
            error.contains("timeout", ignoreCase = true) -> "Operation timed out - try a smaller scope or retry"
            error.contains("network", ignoreCase = true) || error.contains("connect", ignoreCase = true) -> "Network issue - check connectivity and retry"
            else -> null
        }
    }
}