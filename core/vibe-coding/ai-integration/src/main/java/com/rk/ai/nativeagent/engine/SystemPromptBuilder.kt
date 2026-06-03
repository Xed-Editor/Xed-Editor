package com.rk.ai.nativeagent.engine

import com.rk.ai.agent.tools.VibeCodingSystemTools
import com.rk.ai.providers.Model
import com.rk.ai.service.IdeService

class SystemPromptBuilder(
    private val ideService: IdeService,
) {
    private var injected = false
    private var modelPromptInjected = false

    fun isInjected(): Boolean = injected

    fun reset() {
        injected = false
        modelPromptInjected = false
    }

    suspend fun build(model: Model? = null): String {
        if (injected) return ""
        injected = true

        val modelPrompt = if (model != null && !modelPromptInjected) {
            modelPromptInjected = true
            ModelPrompts.forModel(model, VibeCodingSystemTools.SYSTEM_INSTRUCTIONS)
        } else {
            VibeCodingSystemTools.SYSTEM_INSTRUCTIONS
        }

        return (listOf(
            modelPrompt,
            "",
            buildWorkspaceContext(),
            "",
            "Use the available tools to read files, search code, and make changes.",
        )).joinToString("\n")
    }

    private suspend fun buildWorkspaceContext(): String {
        val lines = mutableListOf<String>()
        lines.add("## Current Workspace Context")
        lines.add("")
        try {
            val workspacePath = ideService.getPrimaryWorkspacePath()
            lines.add("Workspace: $workspacePath")
            lines.add("")

            val projectConfig = ideService.getProjectConfig(workspacePath)
            val language = projectConfig["language"]?.asString
            val buildSystem = projectConfig["buildSystem"]?.asString
            if (language != null) lines.add("Language: $language")
            if (buildSystem != null) lines.add("Build System: $buildSystem")
            lines.add("")

            val gitStatus = ideService.getGitStatus(workspacePath)
            val branch = gitStatus["branch"]?.asString ?: "unknown"
            val changes = gitStatus["changes"]?.asJsonArray?.size() ?: 0
            lines.add("Git Branch: $branch ($changes uncommitted changes)")
            lines.add("")

            val openFiles = ideService.getOpenFiles()
            if (openFiles.isNotEmpty()) {
                lines.add("Open Files:")
                openFiles.forEach { f ->
                    val path = f["path"]?.asString ?: f["filePath"]?.asString ?: ""
                    lines.add("  - $path")
                }
                lines.add("")
            }

            val activeFile = ideService.getActiveFile()
            if (activeFile != null) {
                val activePath = activeFile["path"]?.asString ?: activeFile["filePath"]?.asString ?: ""
                lines.add("Active File: $activePath")
                lines.add("")
            }
        } catch (e: Exception) {
            lines.add("(Workspace context unavailable: ${e.message})")
        }
        return lines.joinToString("\n")
    }
}
