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
    val patterns: List<String> = emptyList(),
    val edgeCases: List<String> = emptyList(),
    val securityChecks: List<String> = emptyList(),
    val qualityFlags: List<String> = emptyList(),
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
        val patterns = mutableListOf<String>()
        val edgeCases = mutableListOf<String>()
        val securityChecks = mutableListOf<String>()
        val qualityFlags = mutableListOf<String>()

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
            when {
                toolName == "getFileContent" || toolName == "readFile" || toolName == "cat" -> {
                    if (outputText.contains("null")) {
                        issues.add("File content is null - file may not exist")
                        suggestions.add("Verify the file path exists and try alternative paths")
                    }
                    if (outputText.length < 100 && !outputText.contains("error", ignoreCase = true)) {
                        missingInfo.add("Content seems truncated or unexpectedly short")
                        suggestions.add("File may be empty or not fully read - consider specifying line range")
                    }
                }

                toolName == "writeFile" || toolName == "editFile" || toolName == "applyBatchEdits" -> {
                    if (outputText.contains("Error", ignoreCase = true) || outputText.contains("Failed", ignoreCase = true)) {
                        issues.add("Write/Edit operation reported errors")
                    }
                    if (toolInput.contains(".kt") || toolInput.contains(".java") || toolInput.contains(".kts")) {
                        patterns.add("Code file modified - should run getDiagnostics to verify")
                        suggestions.add("Run getDiagnostics on the modified file")
                    }
                }

                toolName == "listFiles" || toolName == "ls" -> {
                    if (outputText.isBlank() || outputText == "[]") {
                        issues.add("Directory appears empty - may not exist")
                        suggestions.add("Check the parent directory exists and try expanding the path")
                    }
                }

                toolName == "searchCode" || toolName == "grep" || toolName == "searchSymbols" -> {
                    if (outputText.contains("No results") || outputText.startsWith("No ")) {
                        issues.add("Search returned no results")
                        suggestions.add("Try different search terms or check file extensions")
                    }
                    if (outputText.length > 5000) {
                        patterns.add("Search returned many results - might need more specific query")
                    }
                }

                toolName == "runCommand" -> {
                    if (outputText.contains("error", ignoreCase = true) && !outputText.contains("0 error", ignoreCase = true)) {
                        issues.add("Command reported errors")
                        securityChecks.add("Check command output for compilation errors")
                    }
                    if (outputText.contains("warning", ignoreCase = true) && !outputText.contains("0 warning", ignoreCase = true)) {
                        warnings.add("Command reported warnings")
                        qualityFlags.add("Consider fixing warnings for cleaner code")
                    }
                    if (outputText.length > 10000) {
                        patterns.add("Command output is very large - check for relevant error messages")
                    }
                }

                toolName == "getDiagnostics" -> {
                    if (outputText.contains("error", ignoreCase = true)) {
                        issues.add("Diagnostics found errors that need fixing")
                        qualityFlags.add("All diagnostics errors must be resolved before task completion")
                    }
                    if (outputText.contains("warning", ignoreCase = true)) {
                        warnings.add("Diagnostics found warnings - consider addressing them")
                    }
                }

                toolName == "gitCommit" || toolName == "gitPush" -> {
                    if (outputText.contains("error", ignoreCase = true) || outputText.contains("failed", ignoreCase = true)) {
                        issues.add("Git operation failed")
                        suggestions.add("Check git status and resolve conflicts before retrying")
                    }
                }
            }
        }

        // Static code analysis on tool output
        if (outputText.isNotBlank()) {
            // Security patterns
            val securityPatterns = listOf(
                "apiKey" to "Possible API key in code",
                "password" to "Possible password in code",
                "secret" to "Possible secret in code",
                "token" to "Possible token in code (verify it's not hardcoded)",
                "Bearer " to "Bearer token - verify it's not hardcoded",
                "-----BEGIN" to "Possible private key in code",
                "http://" to "Insecure HTTP URL - should use HTTPS",
            )
            for ((pattern, warning) in securityPatterns) {
                if (outputText.contains(pattern, ignoreCase = true)) {
                    securityChecks.add("$pattern: $warning")
                }
            }

            // Quality patterns
            val qualityPatterns = listOf(
                "TODO" to "TODO left in code",
                "FIXME" to "FIXME needs attention",
                "HACK" to "HACK in code - likely needs cleanup",
                "XXX" to "XXX marker in code",
                "printStackTrace" to "printStackTrace should not be used in production",
                "System.out" to "System.out.println in code - use proper logging",
                "e.printStackTrace" to "printStackTrace in catch block",
                "catch (Exception" to "Too broad exception catch",
                "catch (Throwable" to "Catching Throwable is dangerous",
                "null!" to "Null assertion (!) may cause NPE",
                "as " to "Unsafe cast - prefer safe cast (as?)",
                "@Suppress" to "Suppressed warnings",
                "// TODO" to "Unfinished implementation",
            )
            for ((pattern, warning) in qualityPatterns) {
                if (outputText.contains(pattern)) {
                    qualityFlags.add("$warning (match: '$pattern')")
                }
            }
        }

        // Build the review report
        val allIssues = issues + warnings.map { "Warning: $it" } + 
            securityChecks.map { "Security: $it" } + qualityFlags.map { "Quality: $it" }

        return ReviewReport(
            passed = issues.isEmpty() && securityChecks.filter { 
                it.contains("API key", ignoreCase = true) || 
                it.contains("password", ignoreCase = true) || 
                it.contains("secret", ignoreCase = true) ||
                it.contains("private key", ignoreCase = true)
            }.isEmpty(),
            score = when {
                issues.isNotEmpty() -> 30
                securityChecks.isNotEmpty() -> 50
                qualityFlags.isNotEmpty() -> 70
                warnings.isNotEmpty() -> 85
                else -> 100
            },
            feedback = allIssues.joinToString("\n"),
            suggestions = suggestions.distinct(),
            missingInfo = missingInfo,
            warnings = warnings,
            patterns = patterns.distinct(),
            edgeCases = edgeCases.distinct(),
            securityChecks = securityChecks.distinct(),
            qualityFlags = qualityFlags.distinct(),
        )
    }

    fun shouldRetry(report: ReviewReport, attempt: Int, maxAttempts: Int): Boolean {
        if (attempt >= maxAttempts) return false
        if (report.score >= 90) return false
        if (report.warnings.size <= 1 && report.missingInfo.isEmpty() && report.qualityFlags.isEmpty()) return false
        return true
    }

    private fun generateSuggestion(toolName: String, error: String): String? {
        return when {
            error.contains("not found", ignoreCase = true) -> {
                if (toolName in listOf("readFile", "cat", "editFile", "writeFile")) {
                    "Verify the file path using getProjectStructure or listFiles, then retry"
                } else {
                    "Verify the path exists and try listing the parent directory"
                }
            }
            error.contains("permission", ignoreCase = true) -> "Check file permissions or try a different location"
            error.contains("timeout", ignoreCase = true) -> "Operation timed out - try a smaller scope or retry"
            error.contains("network", ignoreCase = true) || error.contains("connect", ignoreCase = true) -> "Network issue - check connectivity and retry"
            error.contains("multiple matches", ignoreCase = true) || error.contains("Found multiple", ignoreCase = true) -> "Provide more surrounding context in oldString, or use replaceAll=true"
            error.contains("Could not find", ignoreCase = true) || error.contains("not found the specified text", ignoreCase = true) -> "Check exact whitespace and content in the file, use dryRun=true first to verify"
            else -> null
        }
    }
}
