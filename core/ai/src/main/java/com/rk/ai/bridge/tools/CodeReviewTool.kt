package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class CodeReviewTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Code Review"
    override fun getName(): String = "codeReview"
    override fun getDescription(): String = """Reviews code for bugs, security issues, performance problems, and style violations. 
Returns structured review with severity levels. Can review a file, selection, or diff."""

    override fun getRequiredParams(): Map<String, String> = mapOf("target" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "focus" to "string",
        "severity" to "string",
        "includeStyle" to "boolean",
        "language" to "string"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "target" to "What to review: file path, 'selection', 'diff', or 'openFiles'"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "focus" to "Review focus: 'bugs', 'security', 'performance', 'all' (default: all)",
        "severity" to "Minimum severity to report: 'error', 'warning', 'info' (default: info)",
        "includeStyle" to "Include style/formatting issues (default: true)",
        "language" to "Override language detection (e.g. 'kotlin', 'python')"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val target = requireString(args, "target")
        val focus = optionalString(args, "focus", "all")
        val severity = optionalString(args, "severity", "info")
        val includeStyle = optionalBoolean(args, "includeStyle", true)
        val language = optionalString(args, "language")

        val code = when (target.lowercase()) {
            "selection" -> {
                val selection = context.ideService.getSelection()
                if (selection.isBlank()) return McpToolResult.error("No selection")
                selection
            }
            "diff" -> {
                val diff = context.ideService.getGitDiff(context.ideService.getPrimaryWorkspacePath())
                if (diff.isBlank()) return McpToolResult.error("No diff available")
                diff
            }
            "openfiles" -> {
                val files = context.ideService.getOpenFiles()
                if (files.isEmpty()) return McpToolResult.error("No open files")
                files.joinToString("\n") { it.toString() }
            }
            else -> {
                val file = resolvePathOrThrow(context, target)
                context.ideService.getFileContent(file.absolutePath)
                    ?: return McpToolResult.error("Could not read file: $target")
            }
        }

        if (code.isBlank()) return McpToolResult.error("No code to review")

        val detectedLang = language ?: detectLanguage(target)
        val reviewPrompt = buildReviewPrompt(code, detectedLang, focus, severity, includeStyle)

        return McpToolResult.success(
            buildString {
                appendLine("## Code Review Request")
                appendLine("**Target:** $target")
                appendLine("**Language:** $detectedLang")
                appendLine("**Focus:** $focus")
                appendLine("**Min Severity:** $severity")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine(reviewPrompt)
                appendLine()
                appendLine("### Code to Review:")
                appendLine("```$detectedLang")
                appendLine(code.take(50000))
                if (code.length > 50000) appendLine("\n... (truncated)")
                appendLine("```")
            },
            emptyMap()
        )
    }

    private fun buildReviewPrompt(code: String, language: String, focus: String, severity: String, includeStyle: Boolean): String {
        return buildString {
            appendLine("You are an expert code reviewer. Analyze the following $language code and provide a structured review.")
            appendLine()
            appendLine("Review criteria:")
            if (focus == "all" || focus == "bugs") {
                appendLine("- **Bugs**: Logic errors, null safety, race conditions, resource leaks, off-by-one errors")
            }
            if (focus == "all" || focus == "security") {
                appendLine("- **Security**: Injection vulnerabilities, hardcoded secrets, insecure crypto, path traversal")
            }
            if (focus == "all" || focus == "performance") {
                appendLine("- **Performance**: N+1 queries, unnecessary allocations, blocking calls, memory leaks")
            }
            if (includeStyle) {
                appendLine("- **Style**: Naming conventions, code organization, dead code, complexity")
            }
            appendLine()
            appendLine("For each issue found, provide:")
            appendLine("1. **Severity**: error | warning | info")
            appendLine("2. **Category**: bug | security | performance | style")
            appendLine("3. **Line number** (approximate if uncertain)")
            appendLine("4. **Description**: Clear explanation of the issue")
            appendLine("5. **Suggestion**: How to fix it")
            appendLine("6. **Code snippet**: The problematic code")
            appendLine()
            appendLine("Respond in JSON format with summary, issues array, and metrics.")
        }
    }

    private fun detectLanguage(path: String): String {
        val ext = path.substringAfterLast(".").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            "tsx", "jsx" -> "tsx"
            "rs" -> "rust"
            "go" -> "go"
            else -> "text"
        }
    }
}
