package com.rk.ai.agent.agents

import com.rk.ai.service.IdeService
import java.io.File

class TestGenerationAgent(private val ideService: IdeService) : SubAgent {
    override val name = "test-generator"
    override val description = "Analyzes source files and generates comprehensive test cases including unit tests, edge cases, and integration tests."
    override val capabilities = listOf(
        AgentCapability("analyze_coverage", "Analyze existing test coverage", "sourceFiles: string[]"),
        AgentCapability("generate_tests", "Generate test cases for specific files", "sourceFiles: string[], framework: string"),
        AgentCapability("find_test_gaps", "Identify untested code paths", "sourceFiles: string[]"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val fileRefs = Regex("""((?:/[^\s]+)+\.\w+)""")
                .findAll(task.prompt + task.contextMessages.joinToString(" "))
                .map { it.value }
                .distinct()
                .take(5)

            val analysis = buildString {
                appendLine("## Test Analysis Request")
                appendLine()
                appendLine("Task: ${task.prompt}")
                if (task.contextMessages.isNotEmpty()) {
                    appendLine()
                    appendLine("Context:")
                    task.contextMessages.forEach { appendLine(it) }
                }
                appendLine()

                val testFramework = when {
                    task.prompt.contains("junit", ignoreCase = true) -> "JUnit 5"
                    task.prompt.contains("mockito", ignoreCase = true) -> "Mockito"
                    task.prompt.contains("pytest", ignoreCase = true) -> "pytest"
                    task.prompt.contains("jest", ignoreCase = true) -> "Jest"
                    else -> "auto-detect"
                }
                appendLine("Test Framework: $testFramework")
                appendLine()

                appendLine("## Source Files to Cover")
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
                    appendLine("(No specific source files referenced)")
                }

                appendLine("## Test Analysis Guidelines")
                appendLine("1. Identify all public functions and methods")
                appendLine("2. Note input parameters and return types")
                appendLine("3. Identify edge cases (null, empty, boundary values)")
                appendLine("4. Note exception/error paths")
                appendLine("5. Identify dependency injection points")
                appendLine("6. Check for existing test files in test/ directory")
            }

            AgentResult.Success(
                output = analysis,
                summary = "Test analysis prepared for ${task.prompt.take(100)}",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Test analysis failed: ${e.message}")
        }
    }
}
