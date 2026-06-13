package com.rk.ai.agent.agents

import com.rk.ai.models.UIMessage
import com.rk.ai.providers.ProviderManager
import com.rk.ai.providers.TextGenerationParams
import com.rk.ai.service.IdeService
import com.rk.ai.persistence.settings.SettingsStore
import com.rk.ai.persistence.settings.findModelById
import com.rk.ai.persistence.settings.getCurrentAssistant

class ArchitectureAgent(
    private val ideService: IdeService,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
) : SubAgent {
    override val name = "architect"
    override val description = "Analyzes codebase structure and designs architecture for new features using AI."
    override val capabilities = listOf(
        AgentCapability("explore", "AI-driven codebase exploration with pattern analysis", "path: string, depth: int"),
        AgentCapability("design", "AI architecture design with trade-off analysis", "requirements: string"),
        AgentCapability("dependency_map", "AI module dependency analysis", "path: string"),
        AgentCapability("migration_plan", "AI-powered incremental refactoring plan", "from: string, to: string"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val maxDepth = if (task.prompt.contains("deep", ignoreCase = true)) 5 else 3
            val structure = ideService.getProjectStructure(workspace, maxDepth, 300)
            val config = ideService.getProjectConfig(workspace)
            val summary = ideService.getProjectSummary()

            val language = config["language"]?.asString ?: "unknown"
            val buildSystem = config["buildSystem"]?.asString ?: "unknown"

            val systemPrompt = buildString {
                appendLine("You are an expert software architect. Analyze the project context and design solutions.")
                appendLine("Your response should cover:")
                appendLine()
                appendLine("## Architecture Analysis")
                appendLine("Current structure, patterns, and conventions observed")
                appendLine()
                appendLine("## Design Approaches (2-3 options)")
                appendLine("For each approach describe:")
                appendLine("- High-level structure and key components")
                appendLine("- Trade-offs (complexity, maintainability, performance)")
                appendLine("- Files that would need to change or be created")
                appendLine()
                appendLine("## Recommended Approach")
                appendLine("State which approach is recommended and why")
                appendLine()
                appendLine("## Implementation Plan")
                appendLine("Step-by-step order of changes")
                appendLine()
                appendLine("Be specific: reference existing file paths, module names, and classes.")
                appendLine("Consider the project's existing patterns before suggesting new ones.")
            }

            val userPrompt = buildString {
                appendLine("## Architecture Request")
                appendLine(task.prompt)
                if (task.contextMessages.isNotEmpty()) {
                    appendLine()
                    appendLine("## Context")
                    task.contextMessages.forEach { appendLine("- $it") }
                }
                appendLine()
                appendLine("## Project Configuration")
                appendLine("- Language: $language")
                appendLine("- Build System: $buildSystem")
                appendLine("- Workspace: $workspace")
                appendLine()
                appendLine("## Project Structure")
                appendLine("```")
                appendLine(structure.take(8000))
                appendLine("```")
                appendLine()
                appendLine("## Project Summary")
                appendLine("```")
                appendLine(summary.take(4000))
                appendLine("```")
            }

            val result = callLlm(systemPrompt, userPrompt)
            AgentResult.Success(
                output = result,
                summary = "AI architecture analysis completed",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Architecture analysis failed: ${e.message}")
        }
    }

    private suspend fun callLlm(systemPrompt: String, userPrompt: String): String {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        val modelId = assistant.chatModelId ?: return fallbackAnalysis(userPrompt)
        val model = settings.findModelById(modelId)
            ?: return fallbackAnalysis(userPrompt)
        val provider = model.findProvider(settings.providers)
            ?: return fallbackAnalysis(userPrompt)

        val providerImpl = providerManager.getProviderByType(provider)
        val messages = listOf(UIMessage.system(systemPrompt), UIMessage.user(userPrompt))

        val chunk = providerImpl.generateText(
            providerSetting = provider,
            messages = messages,
            params = TextGenerationParams(model = model, maxTokens = 4096),
        )
        return chunk.choices.firstOrNull()?.message?.toText() ?: fallbackAnalysis(userPrompt)
    }

    private fun fallbackAnalysis(context: String): String {
        return buildString {
            appendLine("# Architecture Analysis (Fallback)")
            appendLine()
            appendLine("LLM was unavailable. Based on project structure:")
            appendLine("- Review existing module organization before adding new files")
            appendLine("- Follow established patterns (MVVM, Repository, etc.)")
            appendLine("- Keep new abstractions minimal until patterns emerge")
            appendLine("- Consider incremental changes over major rewrites")
        }
    }
}
