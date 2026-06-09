package com.rk.ai.nativeagent.engine

import com.rk.ai.agent.WorkspaceContextCollector
import com.rk.ai.agent.tools.VibeCodingSystemTools
import com.rk.ai.providers.Model
import com.rk.ai.service.IdeService

class SystemPromptBuilder(
    private val ideService: IdeService,
) {
    private var injected = false
    private var modelPromptInjected = false
    private val contextCollector = WorkspaceContextCollector(ideService)
    var projectInstructions: String? = null

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

        val ctxBlock = buildWorkspaceContext()
        val instructionsBlock = buildProjectInstructions()

        return (listOfNotNull(
            modelPrompt,
            "",
            ctxBlock,
            instructionsBlock,
        )).joinToString("\n")
    }

    suspend fun buildWorkspaceContext(): String {
        val snapshot = contextCollector.snapshot()
        if (snapshot.isEmpty()) return ""
        return snapshot.buildContextBlock()
    }

    private fun buildProjectInstructions(): String? {
        val instructions = projectInstructions?.takeIf { it.isNotBlank() } ?: return null
        return buildString {
            appendLine("<project_instructions>")
            appendLine(instructions.trim())
            appendLine("</project_instructions>")
        }
    }
}
