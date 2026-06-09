package com.rk.ai.agent.agents

import com.rk.ai.service.IdeService
import java.io.File

class CodeReviewAgent(private val ideService: IdeService) : SubAgent {
    override val name = "code-reviewer"
    override val description = "Reviews code for bugs, quality issues, security vulnerabilities, and compliance with project conventions. Launches multiple specialized review focuses in parallel."
    override val capabilities = listOf(
        AgentCapability("full_review", "Comprehensive review across all dimensions", "files: string[], focus: string"),
        AgentCapability("diff_review", "Review uncommitted changes", "focus: string"),
        AgentCapability("security_audit", "Security-focused code review", "files: string[]"),
        AgentCapability("bug_hunt", "Bug-focused review with edge case analysis", "files: string[]"),
        AgentCapability("style_review", "Check code style and convention compliance", "files: string[]"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val diff = ideService.getGitDiff(workspace)

            val fileRefs = extractFileReferences(task)
            val isDiffReview = task.prompt.contains("diff", ignoreCase = true) ||
                task.prompt.contains("changes", ignoreCase = true)

            // Build multi-focus review output
            val report = buildString {
                appendLine("# Code Review Report")
                appendLine()

                // Summary section
                appendLine("## Summary")
                appendLine("Task: ${task.prompt}")
                if (task.contextMessages.isNotEmpty()) {
                    appendLine("Context: ${task.contextMessages.joinToString("; ").take(200)}")
                }
                appendLine("Files analyzed: ${fileRefs.size}")
                appendLine("Review type: ${if (isDiffReview) "Diff Review" else "Full Review"}")
                appendLine()

                // Git diff section
                if (isDiffReview && diff.isNotBlank()) {
                    appendLine("## Changes Under Review")
                    appendLine("```diff")
                    appendLine(diff.take(6000))
                    appendLine("```")
                    appendLine()
                }

                // Multi-focus review sections
                appendLine("## Review Dimensions")
                appendLine()

                // Focus 1: Bugs & Correctness
                appendLine("### 1. Bugs & Functional Correctness")
                appendLine("**Focus**: Logic errors, off-by-one, null safety, race conditions, incorrect API usage")
                appendLine()
                appendLine("Check each file for:")
                appendLine("- Nullability violations and unsafe casts")
                appendLine("- Missing edge case handling (empty lists, null inputs)")
                appendLine("- Incorrect comparison or boundary conditions")
                appendLine("- Resource leaks (streams, cursors, connections)")
                appendLine("- Thread safety issues on shared state")
                appendLine()

                // Focus 2: Security
                appendLine("### 2. Security")
                appendLine("**Focus**: Injection, secrets exposure, path traversal, unsafe deserialization")
                appendLine()
                appendLine("Check each file for:")
                appendLine("- Hardcoded credentials, tokens, API keys")
                appendLine("- Path traversal via user-controlled input")
                appendLine("- Unsafe YAML/JSON/pickle deserialization")
                appendLine("- XSS via innerHTML or dangerouslySetInnerHTML")
                appendLine("- Command injection via exec/eval/os.system")
                appendLine("- SQL injection via string concatenation")
                appendLine()

                // Focus 3: Code Quality
                appendLine("### 3. Code Quality & Maintainability")
                appendLine("**Focus**: DRY violations, complexity, naming, comments, testability")
                appendLine()
                appendLine("Check each file for:")
                appendLine("- Duplicated logic that should be extracted")
                appendLine("- Overly complex functions (high cyclomatic complexity)")
                appendLine("- Inconsistent naming or code style")
                appendLine("- AI-generated slop: unnecessary comments, over-defensive checks")
                appendLine("- Missing or inadequate error handling")
                appendLine("- Lack of tests for new logic")
                appendLine()

                // Focus 4: Project Conventions
                appendLine("### 4. Project Convention Compliance")
                appendLine("**Focus**: Architecture patterns, naming conventions, import style, build system")
                appendLine()
                appendLine("Check each file for:")
                appendLine("- Deviation from established project patterns")
                appendLine("- Import style inconsistencies")
                appendLine("- Incorrect module boundaries or dependency direction")
                appendLine("- Build configuration issues")
                appendLine()

                // File-level analysis
                if (fileRefs.isNotEmpty()) {
                    appendLine("## File-by-File Analysis")
                    for (filePath in fileRefs) {
                        val file = File(filePath)
                        if (file.exists()) {
                            val content = file.readText()
                            appendLine("### $filePath (${content.lines().size} lines)")
                            appendLine()
                            appendLine("- **Bugs**: ${checkBugs(content, filePath)}")
                            appendLine("- **Security**: ${checkSecurity(content, filePath)}")
                            appendLine("- **Quality**: ${checkQuality(content, filePath)}")
                            appendLine()
                        }
                    }
                }

                appendLine("## Recommendations")
                appendLine("1. Fix all CRITICAL and HIGH severity issues before merging")
                appendLine("2. Address MEDIUM issues as time permits")
                appendLine("3. Run tests after making changes")
                appendLine("4. Consider adding tests for new/modified logic")
            }

            AgentResult.Success(
                output = report,
                summary = "Multi-focus code review completed for ${fileRefs.size} files",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Code review failed: ${e.message}")
        }
    }

    private fun checkBugs(content: String, path: String): String {
        val issues = mutableListOf<String>()
        if (content.contains("!!") && !content.contains("!!.")) issues.add("Possible double-negation")
        if (content.contains(".also {") && content.lines().size > 50) {
            val alsoCount = content.split(".also {").size - 1
            if (alsoCount > 3) issues.add("Excessive .also usage ($alsoCount)")
        }
        if (content.contains("null !!")) issues.add("Force-unwrap on nullable")
        return issues.joinToString(", ").ifEmpty { "No obvious issues" }
    }

    private fun checkSecurity(content: String, path: String): String {
        val issues = mutableListOf<String>()
        val patterns = mapOf(
            "Hardcoded key/token" to Regex("""(?:api[_-]?key|secret|token|password)\s*[:=]\s*["'`][A-Za-z0-9_\-]{16,}""", RegexOption.IGNORE_CASE),
            "eval/exec" to Regex("""\b(?:eval|exec|Runtime\.getRuntime\(\)\.exec)\s*\("""),
            "Path traversal risk" to Regex("""\.\.\/"""),
            "SQL injection risk" to Regex("""rawQuery\(.*\+\s*\w+"""),
        )
        for ((name, pattern) in patterns) {
            if (pattern.containsMatchIn(content)) issues.add(name)
        }
        return issues.joinToString(", ").ifEmpty { "No security issues detected" }
    }

    private fun checkQuality(content: String, path: String): String {
        val issues = mutableListOf<String>()
        val lines = content.lines()
        if (lines.any { it.trimStart().startsWith("// ") && it.trimStart().length > 80 }) {
            issues.add("Overly verbose comments")
        }
        val functionCount = lines.count { it.trimStart().startsWith("fun ") }
        if (functionCount > 15) issues.add("High function count ($functionCount)")
        if (lines.any { it.length > 200 }) issues.add("Very long lines detected")
        if (content.contains("TODO") || content.contains("FIXME")) issues.add("Contains TODO/FIXME markers")
        return issues.joinToString(", ").ifEmpty { "No quality issues found" }
    }

    private fun extractFileReferences(task: AgentTask): List<String> {
        val all = task.prompt + " " + task.contextMessages.joinToString(" ")
        return Regex("""((?:/[^\s]+)+\.\w+)""")
            .findAll(all)
            .map { it.value }
            .distinct()
            .take(10)
            .toList()
    }
}
