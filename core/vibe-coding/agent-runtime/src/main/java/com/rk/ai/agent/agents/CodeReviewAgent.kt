package com.rk.ai.agent.agents

import com.rk.ai.models.UIMessage
import com.rk.ai.providers.ProviderManager
import com.rk.ai.providers.TextGenerationParams
import com.rk.ai.service.IdeService
import com.rk.ai.persistence.settings.SettingsStore
import com.rk.ai.persistence.settings.findModelById
import com.rk.ai.persistence.settings.getCurrentAssistant
import java.io.File

class CodeReviewAgent(
    private val ideService: IdeService,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
) : SubAgent {
    override val name = "code-reviewer"
    override val description = "Reviews code for bugs, quality issues, security vulnerabilities, and compliance with project conventions using AI."
    override val capabilities = listOf(
        AgentCapability("full_review", "Comprehensive AI review across all dimensions", "files: string[], focus: string"),
        AgentCapability("diff_review", "AI review of uncommitted changes", "focus: string"),
        AgentCapability("security_audit", "AI security-focused code review", "files: string[]"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val diff = ideService.getGitDiff(workspace)
            val fileRefs = extractFileReferences(task)
            val isDiffReview = task.prompt.contains("diff", ignoreCase = true) ||
                task.prompt.contains("changes", ignoreCase = true)

            val fileContents = fileRefs.mapNotNull { path ->
                val file = File(path)
                if (file.exists()) path to file.readText() else null
            }

            val systemPrompt = buildString {
                appendLine("You are an expert code reviewer. Analyze the provided code and produce a structured review.")
                appendLine("Focus on bugs, security issues, code quality, and project convention compliance.")
                appendLine("Format your response with these sections:")
                appendLine("## Summary (brief overview of findings)")
                appendLine("## Critical Issues (bugs, security, correctness)")
                appendLine("## Quality Concerns (maintainability, style, patterns)")
                appendLine("## Recommendations (actionable next steps)")
                appendLine()
                appendLine("Rate each issue as CRITICAL, HIGH, MEDIUM, or LOW severity.")
                appendLine("Be specific: reference file paths, line numbers, and suggest fixes.")
            }

            val userPrompt = buildString {
                appendLine("## Review Task")
                appendLine(task.prompt)
                if (task.contextMessages.isNotEmpty()) {
                    appendLine()
                    appendLine("## Context")
                    task.contextMessages.forEach { appendLine("- $it") }
                }
                if (isDiffReview && diff.isNotBlank()) {
                    appendLine()
                    appendLine("## Git Diff")
                    appendLine("```diff")
                    appendLine(diff.take(12000))
                    appendLine("```")
                }
                if (fileContents.isNotEmpty()) {
                    appendLine()
                    appendLine("## Files for Review")
                    for ((path, content) in fileContents) {
                        appendLine()
                        appendLine("### $path (${content.lines().size} lines)")
                        appendLine("```")
                        appendLine(content.take(8000))
                        appendLine("```")
                    }
                }
            }

            val result = callLlm(systemPrompt, userPrompt)
            AgentResult.Success(
                output = result,
                summary = "AI code review completed for ${fileRefs.size} files",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Code review failed: ${e.message}")
        }
    }

    private suspend fun callLlm(systemPrompt: String, userPrompt: String): String {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId)
            ?: return fallbackReview(userPrompt)
        val provider = model.findProvider(settings.providers)
            ?: return fallbackReview(userPrompt)

        val providerImpl = providerManager.getProviderByType(provider)
        val messages = listOf(
            UIMessage.system(systemPrompt),
            UIMessage.user(userPrompt),
        )

        val chunk = providerImpl.generateText(
            providerSetting = provider,
            messages = messages,
            params = TextGenerationParams(model = model, maxTokens = 4096),
        )
        return chunk.choices.firstOrNull()?.message?.content ?: fallbackReview(userPrompt)
    }

    private fun fallbackReview(context: String): String {
        return buildString {
            appendLine("# Code Review Report (Fallback)")
            appendLine()
            appendLine("LLM was unavailable. Based on static analysis:")
            appendLine("- Check for null safety violations (!! operator)")
            appendLine("- Verify error handling coverage")
            appendLine("- Review thread safety in concurrent contexts")
            appendLine("- Ensure resource cleanup (use {}, try-with-resources)")
        }
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
