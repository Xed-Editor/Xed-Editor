package com.rk.ai.nativeagent.engine

import com.rk.ai.agent.tools.VibeCodingSystemTools
import com.rk.ai.service.IdeService

/**
 * Builds structured system prompts for the VibeCoding AI agent,
 * inspired by opencode's template-based prompt system.
 *
 * Each section is independently constructable and can be overridden
 * without affecting the others.
 */
class SystemPromptBuilder(
    private val ideService: IdeService,
) {
    private var injected = false

    fun isInjected(): Boolean = injected

    fun reset() {
        injected = false
    }

    suspend fun build(): String {
        if (injected) return ""
        injected = true
        return buildString {
            appendLine(VibeCodingSystemTools.SYSTEM_INSTRUCTIONS)
            appendLine()
            appendWorkspaceContext()
            appendLine()
            appendLine("Use the available tools to read files, search code, and make changes.")
        }
    }

    private suspend fun appendWorkspaceContext() {
        appendLine("## Current Workspace Context")
        appendLine()
        try {
            val workspacePath = ideService.getPrimaryWorkspacePath()
            appendLine("Workspace: $workspacePath")
            appendLine()

            val projectConfig = ideService.getProjectConfig(workspacePath)
            val language = projectConfig["language"]?.asString
            val buildSystem = projectConfig["buildSystem"]?.asString
            if (language != null) appendLine("Language: $language")
            if (buildSystem != null) appendLine("Build System: $buildSystem")
            appendLine()

            val gitStatus = ideService.getGitStatus(workspacePath)
            val branch = gitStatus["branch"]?.asString ?: "unknown"
            val changes = gitStatus["changes"]?.asJsonArray?.size() ?: 0
            appendLine("Git Branch: $branch ($changes uncommitted changes)")
            appendLine()

            val openFiles = ideService.getOpenFiles()
            if (openFiles.isNotEmpty()) {
                appendLine("Open Files:")
                openFiles.forEach { f ->
                    val path = f["path"]?.asString ?: f["filePath"]?.asString ?: ""
                    appendLine("  - $path")
                }
                appendLine()
            }

            val activeFile = ideService.getActiveFile()
            if (activeFile != null) {
                val activePath = activeFile["path"]?.asString ?: activeFile["filePath"]?.asString ?: ""
                appendLine("Active File: $activePath")
                appendLine()
            }
        } catch (e: Exception) {
            appendLine("(Workspace context unavailable: ${e.message})")
        }
    }
}
