package com.rk.ai.agent.agents

import com.rk.ai.service.IdeService
import java.io.File

class BugHunterAgent(private val ideService: IdeService) : SubAgent {
    override val name = "bug-hunter"
    override val description = "Analyzes code for bugs, edge cases, null safety issues, race conditions, and error handling gaps. Uses pattern-based detection across multiple dimensions."
    override val capabilities = listOf(
        AgentCapability("static_analysis", "Analyze code for common bug patterns", "files: string[]"),
        AgentCapability("error_flow", "Trace error handling paths for gaps", "files: string[]"),
        AgentCapability("null_safety", "Check for null pointer risks", "files: string[]"),
        AgentCapability("concurrency", "Check for race conditions and thread safety", "files: string[]"),
        AgentCapability("resource_leak", "Detect resource leaks (streams, cursors)", "files: string[]"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val fileRefs = extractFileReferences(task)

            val report = buildString {
                appendLine("# Bug Hunting Report")
                appendLine()

                appendLine("## Task")
                appendLine("${task.prompt}")
                if (task.contextMessages.isNotEmpty()) {
                    appendLine("Context: ${task.contextMessages.joinToString("; ").take(200)}")
                }
                appendLine("Files analyzed: ${fileRefs.size}")
                appendLine()

                if (fileRefs.isEmpty()) {
                    val diff = ideService.getGitDiff(workspace)
                    if (diff.isNotBlank()) {
                        appendLine("## Changes Under Analysis")
                        appendLine("```diff")
                        appendLine(diff.take(6000))
                        appendLine("```")
                        appendLine()
                    }
                }

                fileRefs.forEach { filePath ->
                    val file = File(filePath)
                    if (!file.exists()) return@forEach
                    val content = file.readText()
                    val lines = content.lines()

                    appendLine("## File: $filePath (${lines.size} lines)")
                    appendLine()

                    // Null safety
                    val nullIssues = checkNullSafety(content, lines)
                    if (nullIssues.isNotEmpty()) {
                        appendLine("### Null Safety Issues")
                        nullIssues.forEach { appendLine("- $it") }
                        appendLine()
                    }

                    // Resource leaks
                    val resourceIssues = checkResourceLeaks(content, lines)
                    if (resourceIssues.isNotEmpty()) {
                        appendLine("### Resource Leak Risks")
                        resourceIssues.forEach { appendLine("- $it") }
                        appendLine()
                    }

                    // Thread safety
                    val threadIssues = checkThreadSafety(content, lines)
                    if (threadIssues.isNotEmpty()) {
                        appendLine("### Thread Safety Concerns")
                        threadIssues.forEach { appendLine("- $it") }
                        appendLine()
                    }

                    // Error handling
                    val errorIssues = checkErrorHandling(content, lines)
                    if (errorIssues.isNotEmpty()) {
                        appendLine("### Error Handling Gaps")
                        errorIssues.forEach { appendLine("- $it") }
                        appendLine()
                    }

                    // Edge cases
                    val edgeCases = checkEdgeCases(content, lines)
                    if (edgeCases.isNotEmpty()) {
                        appendLine("### Edge Case Risks")
                        edgeCases.forEach { appendLine("- $it") }
                        appendLine()
                    }

                    appendLine("---")
                    appendLine()
                }
            }

            AgentResult.Success(
                output = report,
                summary = "Bug hunt completed across ${fileRefs.size} files",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Bug hunt failed: ${e.message}")
        }
    }

    private fun checkNullSafety(content: String, lines: List<String>): List<String> {
        val issues = mutableListOf<String>()
        if (content.contains("!!")) issues.add("Force-unwrap operator (!!) used - risk of NPE")
        if (content.contains("null!!")) issues.add("Null value force-unwrapped")
        val lateinitVars = lines.count { it.contains("lateinit var") && !it.contains("by lazy") }
        if (lateinitVars > 0) issues.add("$lateinitVars lateinit vars - risk of UninitializedPropertyAccessException")
        if (content.contains("as ") && !content.contains("as?")) {
            issues.add("Unsafe cast (as) without null-safe alternative (as?)")
        }
        return issues
    }

    private fun checkResourceLeaks(content: String, lines: List<String>): List<String> {
        val issues = mutableListOf<String>()
        if (content.contains("FileInputStream") && !content.contains(".use {") && !content.contains("try("))
            issues.add("FileInputStream without .use{} or try-with-resources")
        if (content.contains("FileOutputStream") && !content.contains(".use {") && !content.contains("try("))
            issues.add("FileOutputStream without .use{} or try-with-resources")
        if (content.contains("Cursor") && !content.contains(".use") && !content.contains("close"))
            issues.add("Cursor may not be closed")
        if (content.contains("database") && content.contains("rawQuery") && !content.contains(".use"))
            issues.add("Database query result may leak")
        return issues
    }

    private fun checkThreadSafety(content: String, lines: List<String>): List<String> {
        val issues = mutableListOf<String>()
        if (content.contains("var ") && !content.contains("volatile") && !content.contains("Atomic") && !content.contains("synchronized")) {
            if (content.contains("Thread") || content.contains("coroutine") || content.contains("launch {"))
                issues.add("Mutable state (var) without synchronization in concurrent context")
        }
        if (content.contains("MutableList") || content.contains("mutableListOf") || content.contains("ArrayList")) {
            if (content.contains("launch {") || content.contains("async {"))
                issues.add("Mutable collection used in coroutine context - risk of concurrent modification")
        }
        if (content.contains("object ") && content.contains("var ")) {
            if (!content.contains("synchronized") && !content.contains("Atomic"))
                issues.add("Singleton object with mutable state without synchronization")
        }
        return issues
    }

    private fun checkErrorHandling(content: String, lines: List<String>): List<String> {
        val issues = mutableListOf<String>()
        if (content.contains("runCatching") && !content.contains(".onFailure"))
            issues.add("runCatching without onFailure handler - errors may be silently swallowed")
        if (content.contains("catch (e: Exception)") && content.contains("e.printStackTrace()") || content.contains("Log.e"))
            issues.add("Generic Exception catch with only logging - consider more specific handling")
        if (content.contains("catch (e: Throwable)"))
            issues.add("Catching Throwable is too broad - catches OutOfMemoryError etc.")
        if (content.contains("?.let") && !content.contains("?: return"))
            issues.add("?.let without else branch - null case silently ignored")
        val tryCount = lines.count { it.trimStart().startsWith("try {") }
        val catchCount = lines.count { it.trimStart().startsWith("} catch") }
        if (tryCount > catchCount + 1) issues.add("More try blocks than catch blocks - possible unhandled exceptions")
        return issues
    }

    private fun checkEdgeCases(content: String, lines: List<String>): List<String> {
        val issues = mutableListOf<String>()
        if (content.contains(".first()") || content.contains(".last()"))
            issues.add(".first()/.last() on potentially empty collection throws NoSuchElementException")
        if (content.contains("substring") && !content.contains("coerceIn") && !content.contains("if"))
            issues.add("substring without bounds check - IndexOutOfBounds risk")
        if (content.contains("toInt()") && !content.contains("toIntOrNull"))
            issues.add("toInt() may throw NumberFormatException - consider toIntOrNull()")
        if (content.contains("toLong()") && !content.contains("toLongOrNull"))
            issues.add("toLong() may throw NumberFormatException - consider toLongOrNull()")
        if (content.contains("get(") && !content.contains("getOrNull")) {
            if (content.contains("List") || content.contains("listOf") || content.contains("ArrayList"))
                issues.add("List.get() without bounds check - IndexOutOfBoundsException risk")
        }
        return issues
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
