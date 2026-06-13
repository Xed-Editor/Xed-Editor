package com.rk.ai.agent.agents

import com.rk.ai.models.UIMessage
import com.rk.ai.providers.ProviderManager
import com.rk.ai.providers.TextGenerationParams
import com.rk.ai.service.IdeService
import com.rk.ai.persistence.settings.SettingsStore
import com.rk.ai.persistence.settings.findModelById
import com.rk.ai.persistence.settings.getCurrentAssistant
import java.io.File

class TestGenerationAgent(
    private val ideService: IdeService,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
) : SubAgent {
    override val name = "test-generator"
    override val description = "Analyzes source files and generates comprehensive test cases including unit tests, edge cases, and integration tests using AI."
    override val capabilities = listOf(
        AgentCapability("analyze_coverage", "AI analysis of existing test coverage and gaps", "sourceFiles: string[]"),
        AgentCapability("generate_tests", "AI generation of test cases for specific files", "sourceFiles: string[], framework: string"),
        AgentCapability("find_test_gaps", "AI identification of untested code paths", "sourceFiles: string[]"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val fileRefs = extractFileReferences(task)

            val fileContents = fileRefs.mapNotNull { path ->
                val file = File(path)
                if (file.exists()) path to file.readText() else null
            }

            if (fileContents.isEmpty()) {
                return AgentResult.Success(
                    output = "No source files found to analyze for test generation.",
                    summary = "No files to analyze",
                )
            }

            val testFramework = detectTestFramework(workspace)

            val systemPrompt = buildString {
                appendLine("You are an expert test engineer. Analyze the provided source files and generate test cases.")
                appendLine("Detected test framework: $testFramework")
                appendLine()
                appendLine("Your response should cover:")
                appendLine()
                appendLine("## Test Analysis")
                appendLine("- Public API surface (functions, classes)")
                appendLine("- Existing test coverage (if any)")
                appendLine("- Test gaps and risks")
                appendLine()
                appendLine("## Generated Test Cases")
                appendLine("For each function/class, provide:")
                appendLine("- Normal/positive test case")
                appendLine("- Edge cases (empty, null, boundary)")
                appendLine("- Error paths (exceptions, failures)")
                appendLine("- Suggested test code in the detected framework style")
                appendLine()
                appendLine("## Coverage Recommendations")
                appendLine("- Which tests to prioritize")
                appendLine("- Integration test suggestions")
                appendLine("- Mock/stub dependencies needed")
                appendLine()
                appendLine("Format test code in proper runnable form using the detected framework.")
            }

            val userPrompt = buildString {
                appendLine("## Test Generation Task")
                appendLine(task.prompt)
                if (task.contextMessages.isNotEmpty()) {
                    appendLine()
                    appendLine("## Context")
                    task.contextMessages.forEach { appendLine("- $it") }
                }
                appendLine()
                appendLine("## Workspace: $workspace")
                appendLine("## Detected Framework: $testFramework")
                appendLine()
                for ((path, content) in fileContents) {
                    appendLine()
                    appendLine("### Source: $path (${content.lines().size} lines)")
                    appendLine("```")
                    appendLine(content.take(10000))
                    appendLine("```")
                }
            }

            val result = callLlm(systemPrompt, userPrompt)
            AgentResult.Success(
                output = result,
                summary = "AI test analysis completed for ${fileRefs.size} source files",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Test analysis failed: ${e.message}")
        }
    }

    private suspend fun callLlm(systemPrompt: String, userPrompt: String): String {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId)
            ?: return fallbackGeneration(userPrompt)
        val provider = model.findProvider(settings.providers)
            ?: return fallbackGeneration(userPrompt)

        val providerImpl = providerManager.getProviderByType(provider)
        val messages = listOf(UIMessage.system(systemPrompt), UIMessage.user(userPrompt))

        val chunk = providerImpl.generateText(
            providerSetting = provider,
            messages = messages,
            params = TextGenerationParams(model = model, maxTokens = 4096),
        )
        return chunk.choices.firstOrNull()?.message?.toText() ?: fallbackGeneration(userPrompt)
    }

    private fun fallbackGeneration(context: String): String {
        return buildString {
            appendLine("# Test Analysis (Fallback)")
            appendLine()
            appendLine("LLM was unavailable. Manual testing checklist:")
            appendLine("- Write unit tests for each public function")
            appendLine("- Cover null/empty/boundary inputs")
            appendLine("- Test error handling paths")
            appendLine("- Verify edge cases in conditional logic")
            appendLine("- Consider integration tests for cross-module flows")
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
        return "unknown"
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
}
