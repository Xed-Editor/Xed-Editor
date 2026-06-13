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
            when (part) {
                is UIMessagePart.Text -> part.text
                else -> ""
            }
        }

        if (outputText.isBlank()) {
            missingInfo.add("Tool '$toolName' returned empty result")
        }

        if (outputText.contains("not found", ignoreCase = true) && !toolInput.contains("notfound")) {
            warnings.add("Tool '$toolName' reports resource not found")
        }

        if (outputText.contains("error", ignoreCase = true)) {
            warnings.add("Tool '$toolName' output contains error text")
        }

        if (outputText.contains("permission denied", ignoreCase = true)) {
            issues.add("Permission denied for '$toolName'")
        }

        val repeatedPatterns = listOf(
            "Tool '$toolName' has been called repeatedly" to "Check if the tool's result is what you expect",
            "missing required argument" to "Check tool parameters and provide all required arguments",
        )
        for ((pattern, suggestion) in repeatedPatterns) {
            if (outputText.contains(pattern, ignoreCase = true)) {
                missingInfo.add("Tool '$toolName': $pattern")
                suggestions.add(suggestion)
            }
        }

        if (executionState is ExecutionState.Completed && outputText.length > 100_000) {
            warnings.add("Tool '$toolName' returned very large output (${outputText.length} chars)")
        }

        val score = when {
            issues.isNotEmpty() && missingInfo.isNotEmpty() -> maxOf(0, 20 - issues.size * 10)
            issues.isNotEmpty() -> maxOf(0, 40 - issues.size * 10)
            warnings.isNotEmpty() -> maxOf(50, 80 - warnings.size * 10)
            missingInfo.isNotEmpty() -> maxOf(60, 90 - missingInfo.size * 10)
            else -> 100
        }

        val passed = issues.isEmpty() && missingInfo.size <= 2

        return ReviewReport(
            passed = passed,
            score = score,
            feedback = issues.joinToString("\n"),
            suggestions = suggestions,
            missingInfo = missingInfo,
            warnings = warnings,
        )
    }

    fun isInformationSufficient(
        toolName: String,
        result: List<UIMessagePart>,
        goal: String,
    ): ReviewReport {
        val outputText = result.joinToString("\n") { when (it) { is UIMessagePart.Text -> it.text; else -> "" } }

        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        if (toolName == "readFile" || toolName == "readFiles" || toolName == "cat") {
            if (outputText.contains("FILE NOT FOUND") || outputText.contains("not found")) {
                issues.add("File not found - need to check alternative locations")
                suggestions.add("Try searching for the file with findFiles or searchCode")
            }
            if (outputText.length < 50 && !outputText.contains("error", ignoreCase = true)) {
                suggestions.add("File may be empty or not fully read - consider specifying line range")
            }
        }

        if (toolName == "searchCode" || toolName == "grep" || toolName == "searchSymbols") {
            if (outputText.contains("No results") || outputText.startsWith("No ")) {
                issues.add("Search returned no results")
                suggestions.add("Try different search terms or check file extensions")
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
        if (report.warnings.size <= 2 && report.issues.isEmpty()) return false
        return true
    }

    private fun generateSuggestion(toolName: String, error: String): String? {
        return when {
            error.contains("not found", ignoreCase = true) -> "Verify the path exists and try listing the parent directory"
            error.contains("permission", ignoreCase = true) -> "Check file permissions or try a different location"
            error.contains("timeout", ignoreCase = true) -> "Break the operation into smaller parts"
            error.contains("invalid", ignoreCase = true) -> "Check tool arguments for correct format"
            error.contains("missing", ignoreCase = true) -> "Provide all required parameters"
            error.contains("connection", ignoreCase = true) -> "Check network connectivity and retry"
            else -> null
        }
    }
}
