package com.rk.ai.agent.agents

import com.rk.ai.service.IdeService
import java.io.File

class CodeReviewAgent(private val ideService: IdeService) : SubAgent {
    override val name = "code-reviewer"
    override val description = "Reviews code for bugs, quality issues, security vulnerabilities, and compliance with project conventions."
    override val capabilities = listOf(
        AgentCapability("code_review", "Review specific files or changes for issues", "files: string[], focus: string"),
        AgentCapability("diff_review", "Review uncommitted changes", "focus: string"),
        AgentCapability("security_audit", "Security-focused code review", "files: string[]"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val diff = ideService.getGitDiff(workspace)

            val reviewPrompt = buildString {
                appendLine("## Code Review Request")
                appendLine()
                appendLine("Task: ${task.prompt}")
                if (task.contextMessages.isNotEmpty()) {
                    appendLine()
                    appendLine("Context:")
                    task.contextMessages.forEach { appendLine(it) }
                }
                appendLine()

                val staged = task.prompt.contains("diff", ignoreCase = true) ||
                    task.prompt.contains("changes", ignoreCase = true)
                if (staged && diff.isNotBlank()) {
                    appendLine("## Git Diff (Uncommitted Changes)")
                    appendLine(diff.take(8000))
                    appendLine()
                }

                val fileRefs = Regex("""((?:/[^\s]+)+\.\w+)""")
                    .findAll(task.prompt + task.contextMessages.joinToString(" "))
                    .map { it.value }
                    .distinct()
                    .take(5)

                if (fileRefs.any()) {
                    appendLine("## Referenced Files")
                    fileRefs.forEach { filePath ->
                        val file = File(filePath)
                        if (file.exists()) {
                            appendLine("### $filePath")
                            appendLine("```")
                            appendLine(file.readText().take(3000))
                            appendLine("```")
                            appendLine()
                        }
                    }
                }
            }

            AgentResult.Success(
                output = reviewPrompt,
                summary = "Code review analysis prepared for ${task.prompt.take(100)}",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Code review failed: ${e.message}")
        }
    }
}
