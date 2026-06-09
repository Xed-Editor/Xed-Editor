package com.rk.ai.agent.agents

import com.rk.ai.service.IdeService
import java.io.File

class TestGenerationAgent(private val ideService: IdeService) : SubAgent {
    override val name = "test-generator"
    override val description = "Analyzes source files and generates comprehensive test cases including unit tests, edge cases, and integration tests with coverage analysis."
    override val capabilities = listOf(
        AgentCapability("analyze_coverage", "Analyze existing test coverage and find gaps", "sourceFiles: string[]"),
        AgentCapability("generate_tests", "Generate test cases for specific files", "sourceFiles: string[], framework: string"),
        AgentCapability("find_test_gaps", "Identify untested code paths and edge cases", "sourceFiles: string[]"),
        AgentCapability("generate_mocks", "Generate mock definitions for dependencies", "sourceFiles: string[]"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val fileRefs = extractFileReferences(task)

            val testFramework = detectTestFramework(workspace)
            val report = buildString {
                appendLine("# Test Analysis Report")
                appendLine()

                appendLine("## Task")
                appendLine("${task.prompt}")
                if (task.contextMessages.isNotEmpty()) {
                    appendLine("Context: ${task.contextMessages.joinToString("; ").take(200)}")
                }
                appendLine("Files to cover: ${fileRefs.size}")
                appendLine()

                appendLine("## Environment")
                appendLine("- Detected test framework: $testFramework")
                appendLine("- Workspace: $workspace")
                appendLine()

                val existingTests = findExistingTests(workspace, fileRefs)
                if (existingTests.isNotEmpty()) {
                    appendLine("## Existing Test Files")
                    existingTests.forEach { (source, testFile) ->
                        appendLine("- $source → $testFile")
                    }
                    appendLine()
                }

                fileRefs.forEach { filePath ->
                    val file = File(filePath)
                    if (!file.exists()) return@forEach
                    val content = file.readText()

                    appendLine("## Source: $filePath")
                    appendLine()

                    val functions = extractFunctions(content)
                    appendLine("### Public API (${functions.size} functions)")
                    functions.forEach { fn ->
                        appendLine("- `${fn.name}`: ${fn.description}")
                    }
                    appendLine()

                    appendLine("### Test Cases Needed")
                    appendLine()
                    appendLine("#### Unit Tests")
                    functions.forEachIndexed { i, fn ->
                        appendLine("${i + 1}. `${fn.name}`")
                        appendLine("   - Normal case: ${fn.normalCase}")
                        appendLine("   - Null/empty input: ${fn.nullCase}")
                        appendLine("   - Boundary: ${fn.boundaryCase}")
                        appendLine("   - Error path: ${fn.errorCase}")
                    }
                    appendLine()

                    appendLine("#### Edge Cases to Cover")
                    appendLine("- Empty collection / null input")
                    appendLine("- Maximum values / overflow")
                    appendLine("- Concurrent access (if applicable)")
                    appendLine("- Invalid state transitions")
                    appendLine("- Resource exhaustion")
                    appendLine("- Unexpected input types")
                    appendLine()

                    appendLine("#### Suggested Test Structure")
                    val testClassName = file.nameWithoutExtension + "Test"
                    appendLine("```")
                    appendLine("class $testClassName {")
                    appendLine("    @Test")
                    appendLine("    fun `test normal case`() {")
                    appendLine("        // Arrange")
                    appendLine("        // Act")
                    appendLine("        // Assert")
                    appendLine("    }")
                    appendLine("}")
                    appendLine("```")
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
            }

            AgentResult.Success(
                output = report,
                summary = "Test analysis prepared for ${fileRefs.size} source files using $testFramework",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Test analysis failed: ${e.message}")
        }
    }

    private fun detectTestFramework(workspace: String): String {
        val buildFiles = listOf(
            File(workspace, "build.gradle.kts"),
            File(workspace, "build.gradle"),
            File(workspace, "pom.xml"),
            File(workspace, "package.json"),
        )
        for (file in buildFiles) {
            if (!file.exists()) continue
            val content = file.readText()
            when {
                "junit" in content.lowercase() && "mockito" in content.lowercase() -> return "JUnit 5 + Mockito"
                "junit" in content.lowercase() -> return "JUnit 5"
                "kotlin.test" in content -> return "kotlin.test"
                "jest" in content.lowercase() -> return "Jest"
                "pytest" in content.lowercase() || "unittest" in content.lowercase() -> return "pytest"
                "mocha" in content.lowercase() -> return "Mocha"
                "jasmine" in content.lowercase() -> return "Jasmine"
            }
        }
        return "unknown (check project configuration)"
    }

    private fun findExistingTests(workspace: String, sourceFiles: List<String>): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        for (src in sourceFiles) {
            val srcFile = File(src)
            val testName = srcFile.nameWithoutExtension + "Test." + srcFile.extension
            val testDir = File(workspace, "src/test")
            if (testDir.exists()) {
                val testFiles = testDir.walkTopDown()
                    .filter { it.name == testName || it.nameWithoutExtension == srcFile.nameWithoutExtension + "Test" }
                    .toList()
                for (tf in testFiles) {
                    results.add(src to tf.absolutePath)
                }
            }
        }
        return results
    }

    private fun extractFunctions(content: String): List<FunctionInfo> {
        val functions = mutableListOf<FunctionInfo>()
        val funRegex = Regex("""(fun\s+(\w+)\s*\(([^)]*)\))\s*(\??\.\s*[^:{]*)?\s*""")
        for (match in funRegex.findAll(content)) {
            val name = match.groupValues[2]
            if (name in listOf("main", "toString", "hashCode", "equals", "copy", "component")) continue
            val params = match.groupValues[3]
            functions.add(FunctionInfo(
                name = name,
                description = "fun $name($params)",
                normalCase = "Call with valid $params",
                nullCase = "Call with null/missing parameters",
                boundaryCase = "Call with min/max/edge values",
                errorCase = "Call with invalid inputs to trigger error handling",
            ))
        }
        return functions
    }

    private fun extractFileReferences(task: AgentTask): List<String> {
        val all = task.prompt + " " + task.contextMessages.joinToString(" ")
        return Regex("""((?:/[^\s]+)+\.\w+)""")
            .findAll(all)
            .map { it.value }
            .distinct()
            .take(5)
            .toList()
    }

    private data class FunctionInfo(
        val name: String,
        val description: String,
        val normalCase: String,
        val nullCase: String,
        val boundaryCase: String,
        val errorCase: String,
    )
}
