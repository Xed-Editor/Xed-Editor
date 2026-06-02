package com.rk.ai.agent.agents

import com.rk.ai.service.IdeService
import java.io.File

class BugHunterAgent(private val ideService: IdeService) : SubAgent {
    override val name = "bug-hunter"
    override val description = "Analyzes code for bugs, edge cases, null safety issues, race conditions, and error handling gaps."
    override val capabilities = listOf(
        AgentCapability("static_analysis", "Analyze code for common bug patterns", "files: string[]"),
        AgentCapability("error_flow", "Trace error handling paths", "files: string[]"),
        AgentCapability("null_safety", "Check for null pointer risks", "files: string[]"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val fileRefs = Regex("""((?:/[^\s]+)+\.\w+)""")
                .findAll(task.prompt + task.contextMessages.joinToString(" "))
                .map { it.value }
                .distinct()
                .take(10)

            val analysisContext = buildString {
                appendLine("## Bug Hunting Analysis Request")
                appendLine()
                appendLine("Task: ${task.prompt}")
                if (task.contextMessages.isNotEmpty()) {
                    appendLine()
                    appendLine("Context:")
                    task.contextMessages.forEach { appendLine(it) }
                }
                appendLine()

                appendLine("## Analysis Focus Areas")
                appendLine("- Null pointer / type safety issues")
                appendLine("- Race conditions / thread safety")
                appendLine("- Resource leaks (files, connections, streams)")
                appendLine("- Error handling gaps (uncaught exceptions)")
                appendLine("- Edge cases (empty inputs, boundary values)")
                appendLine("- Logic errors in conditionals and loops")
                appendLine("- Security vulnerabilities (injection, XSS, path traversal)")
                appendLine()

                appendLine("## Files to Analyze")
                if (fileRefs.any()) {
                    fileRefs.forEach { filePath ->
                        val file = File(filePath)
                        if (file.exists()) {
                            appendLine("### $filePath")
                            appendLine("```")
                            appendLine(file.readText().take(4000))
                            appendLine("```")
                            appendLine()
                        }
                    }
                } else {
                    appendLine("(No specific files referenced - analysis will be context-based)")
                }

                val diff = ideService.getGitDiff(workspace)
                if (diff.isNotBlank()) {
                    appendLine("## Current Changes (for context)")
                    appendLine(diff.take(5000))
                }
            }

            AgentResult.Success(
                output = analysisContext,
                summary = "Bug hunt analysis prepared for ${task.prompt.take(100)}",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Bug hunt failed: ${e.message}")
        }
    }
}
