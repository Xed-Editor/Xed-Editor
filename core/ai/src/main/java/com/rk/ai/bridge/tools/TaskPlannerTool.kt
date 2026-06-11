package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class TaskPlannerTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Planning"
    override fun getName(): String = "planTask"
    override fun getDescription(): String = """Breaks down complex coding tasks into executable steps."""

    override fun getRequiredParams(): Map<String, String> = mapOf("task" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "context" to "string",
        "constraints" to "string",
        "maxSteps" to "number",
        "includeTests" to "boolean"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "task" to "Description of the task to plan"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "context" to "Additional context: file paths, code snippets, or project info",
        "constraints" to "Constraints: time, dependencies, compatibility requirements",
        "maxSteps" to "Maximum number of steps (default: 10)",
        "includeTests" to "Include testing steps (default: true)"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val task = requireString(args, "task")
        val taskContext = optionalString(args, "context")
        val constraints = optionalString(args, "constraints")
        val maxSteps = optionalInt(args, "maxSteps") ?: 10
        val includeTests = optionalBoolean(args, "includeTests", true)

        val workspacePath = context.ideService.getPrimaryWorkspacePath()
        val projectStructure = context.ideService.getProjectStructure(workspacePath, 5, 200)

        val planPrompt = buildPlanPrompt(task, taskContext, constraints, maxSteps, includeTests, projectStructure)

        return McpToolResult.success(
            buildString {
                appendLine("## Task Planning Request")
                appendLine("**Task:** $task")
                if (!taskContext.isNullOrBlank()) appendLine("**Context:** $taskContext")
                if (!constraints.isNullOrBlank()) appendLine("**Constraints:** $constraints")
                appendLine("**Max Steps:** $maxSteps")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine(planPrompt)
            },
            emptyMap()
        )
    }

    private fun buildPlanPrompt(
        task: String,
        context: String?,
        constraints: String?,
        maxSteps: Int,
        includeTests: Boolean,
        projectStructure: String
    ): String = buildString {
        appendLine("You are an expert software architect. Create a detailed execution plan for the following task.")
        appendLine()
        appendLine("### Task:")
        appendLine(task)
        if (!context.isNullOrBlank()) {
            appendLine()
            appendLine("### Additional Context:")
            appendLine(context)
        }
        if (!constraints.isNullOrBlank()) {
            appendLine()
            appendLine("### Constraints:")
            appendLine(constraints)
        }
        if (projectStructure.isNotBlank()) {
            appendLine()
            appendLine("### Project Structure:")
            appendLine(projectStructure.take(5000))
        }
        appendLine()
        appendLine("### Plan Requirements:")
        appendLine("- Break the task into $maxSteps or fewer clear, actionable steps")
        appendLine("- Each step should be independently testable")
        appendLine("- Identify dependencies between steps")
        appendLine("- Estimate complexity for each step")
        if (includeTests) {
            appendLine("- Include testing steps after implementation steps")
        }
    }
}
