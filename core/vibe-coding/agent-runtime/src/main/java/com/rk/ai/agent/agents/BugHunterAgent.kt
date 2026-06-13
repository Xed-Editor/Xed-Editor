@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.agent.agents

import com.rk.ai.models.UIMessage
import com.rk.ai.providers.ProviderManager
import com.rk.ai.providers.ProviderSetting
import com.rk.ai.providers.TextGenerationParams
import com.rk.ai.service.IdeService
import com.rk.ai.persistence.settings.SettingsStore
import com.rk.ai.persistence.settings.findModelById
import com.rk.ai.persistence.settings.findProvider
import com.rk.ai.persistence.settings.getCurrentAssistant
import java.io.File
import kotlin.uuid.ExperimentalUuidApi

class BugHunterAgent(
    private val ideService: IdeService,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
) : SubAgent {
    override val name = "bug-hunter"
    override val description = "Analyzes code for bugs, edge cases, null safety issues, race conditions, and error handling gaps using AI."
    override val capabilities = listOf(
        AgentCapability("static_analysis", "AI-powered analysis for common bug patterns", "files: string[]"),
        AgentCapability("error_flow", "AI trace of error handling paths for gaps", "files: string[]"),
        AgentCapability("null_safety", "AI check for null pointer risks", "files: string[]"),
        AgentCapability("concurrency", "AI check for race conditions and thread safety", "files: string[]"),
        AgentCapability("resource_leak", "AI detection of resource leaks", "files: string[]"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val fileRefs = extractFileReferences(task)
            val diff = ideService.getGitDiff(workspace)

            val fileContents = fileRefs.mapNotNull { path ->
                val file = File(path)
                if (file.exists()) path to file.readText() else null
            }

            if (fileContents.isEmpty() && diff.isBlank()) {
                return AgentResult.Success(
                    output = "No files or changes found to analyze.",
                    summary = "No code to analyze",
                )
            }

            val systemPrompt = buildString {
                appendLine("You are an expert bug hunter. Analyze the provided code and identify all potential bugs.")
                appendLine("Cover these categories:")
                appendLine("1. **Null Safety Issues** - NPE risks, unsafe casts, force-unwrap")
                appendLine("2. **Resource Leaks** - unclosed streams, cursors, connections")
                appendLine("3. **Thread Safety** - race conditions, shared mutable state")
                appendLine("4. **Error Handling** - unhandled exceptions, silent failures")
                appendLine("5. **Edge Cases** - boundary conditions, empty collections, overflow")
                appendLine()
                appendLine("For each bug found, specify:")
                appendLine("- **File** and **line** (approximate)")
                appendLine("- **Severity**: CRITICAL / HIGH / MEDIUM / LOW")
                appendLine("- **Description** of the issue")
                appendLine("- **Fix suggestion** (code snippet preferred)")
            }

            val userPrompt = buildString {
                appendLine("## Bug Hunt Task")
                appendLine(task.prompt)
                if (task.contextMessages.isNotEmpty()) {
                    appendLine()
                    appendLine("## Context")
                    task.contextMessages.forEach { appendLine("- $it") }
                }
                if (diff.isNotBlank()) {
                    appendLine()
                    appendLine("## Changes Under Analysis")
                    appendLine("```diff")
                    appendLine(diff.take(10000))
                    appendLine("```")
                }
                for ((path, content) in fileContents) {
                    appendLine()
                    appendLine("### $path (${content.lines().size} lines)")
                    appendLine("```")
                    appendLine(content.take(8000))
                    appendLine("```")
                }
            }

            val result = callLlm(systemPrompt, userPrompt)
            AgentResult.Success(
                output = result,
                summary = "AI bug hunt completed for ${fileRefs.size} files",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Bug hunt failed: ${e.message}")
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

        val providerImpl = providerManager.getProviderByType<ProviderSetting>(provider)
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
            appendLine("# Bug Hunt Report (Fallback)")
            appendLine()
            appendLine("LLM was unavailable. Manual checklist:")
            appendLine("- Check for `!!` force-unwrap operators")
            appendLine("- Verify resource cleanup (FileInputStream, Cursor, database)")
            appendLine("- Check mutable state in concurrent contexts")
            appendLine("- Look for broad Exception catches")
            appendLine("- Check .first()/.last() on potentially empty collections")
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
