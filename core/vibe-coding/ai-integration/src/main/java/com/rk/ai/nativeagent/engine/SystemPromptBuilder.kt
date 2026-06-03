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
            append(VibeCodingSystemTools.SYSTEM_INSTRUCTIONS + "\n")
            append("\n")
            appendWorkspaceContext()
            append("\n")
            append("Use the available tools to read files, search code, and make changes.\n")
        }
    }

    private suspend fun appendWorkspaceContext() {
        append("## Current Workspace Context\n")
        append("\n")
        try {
            val workspacePath = ideService.getPrimaryWorkspacePath()
            append("Workspace: $workspacePath\n")
            append("\n")

            val projectConfig = ideService.getProjectConfig(workspacePath)
            val language = projectConfig["language"]?.asString
            val buildSystem = projectConfig["buildSystem"]?.asString
            if (language != null) append("Language: $language\n")
            if (buildSystem != null) append("Build System: $buildSystem\n")
            append("\n")

            val gitStatus = ideService.getGitStatus(workspacePath)
            val branch = gitStatus["branch"]?.asString ?: "unknown"
            val changes = gitStatus["changes"]?.asJsonArray?.size() ?: 0
            append("Git Branch: $branch ($changes uncommitted changes)\n")
            append("\n")

            val openFiles = ideService.getOpenFiles()
            if (openFiles.isNotEmpty()) {
                append("Open Files:\n")
                openFiles.forEach { f ->
                    val path = f["path"]?.asString ?: f["filePath"]?.asString ?: ""
                    append("  - $path\n")
                }
                append("\n")
            }

            val activeFile = ideService.getActiveFile()
            if (activeFile != null) {
                val activePath = activeFile["path"]?.asString ?: activeFile["filePath"]?.asString ?: ""
                append("Active File: $activePath\n")
                append("\n")
            }
        } catch (e: Exception) {
            append("(Workspace context unavailable: ${e.message})\n")
        }
    }
}
