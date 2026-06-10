package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class TaskPlannerTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Planning"
    override fun getName(): String = "planTask"
    override fun getDescription(): String = """Breaks down complex coding tasks into executable steps. 
Creates a structured plan with dependencies, risks, and estimated complexity."""

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

        // Gather project context automatically
        val projectStructure = context.ideService.getProjectStructure(
            context.ideService.getPrimaryWorkspacePath()
        )
        val openFiles = context.ideService.getOpenFiles()
        val projectConfig = context.ideService.getProjectConfig()

        val planPrompt = buildPlanPrompt(task, taskContext, constraints, maxSteps, includeTests,
            projectStructure, openFiles, projectConfig)

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
            mapOf(
                "task" to task,
                "maxSteps" to maxSteps,
                "includeTests" to includeTests
            )
        )
    }

    private fun buildPlanPrompt(
        task: String,
        context: String?,
        constraints: String?,
        maxSteps: Int,
        includeTests: Boolean,
        projectStructure: String?,
        openFiles: String?,
        projectConfig: String?
    ): String {
        return buildString {
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

            if (!projectStructure.isNullOrBlank()) {
                appendLine()
                appendLine("### Project Structure:")
                appendLine(projectStructure.take(5000))
            }

            if (!openFiles.isNullOrBlank()) {
                appendLine()
                appendLine("### Open Files:")
                appendLine(openFiles.take(2000))
            }

            if (!projectConfig.isNullOrBlank()) {
                appendLine()
                appendLine("### Project Config:")
                appendLine(projectConfig.take(1000))
            }

            appendLine()
            appendLine("### Plan Requirements:")
            appendLine("- Break the task into $maxSteps or fewer clear, actionable steps")
            appendLine("- Each step should be independently testable")
            appendLine("- Identify dependencies between steps")
            appendLine("- Estimate complexity for each step (low/medium/high)")
            appendLine("- Identify potential risks and mitigation strategies")
            if (includeTests) {
                appendLine("- Include testing steps after implementation steps")
            }
            appendLine()
            appendLine("### Respond in this JSON format:")
            appendLine("```json")
            appendLine("""{
  "summary": "Brief overview of the approach",
  "steps": [
    {
      "id": 1,
      "title": "Step title",
      "description": "Detailed description of what to do",
      "files": ["files to modify/create"],
      "dependencies": [0],
      "complexity": "low|medium|high",
      "risks": ["potential issues"],
      "testCriteria": "how to verify this step is complete"
    }
  ],
  "risks": [
    {
      "description": "Risk description",
      "likelihood": "low|medium|high",
      "impact": "low|medium|high",
      "mitigation": "How to mitigate"
    }
  ],
  "totalComplexity": "low|medium|high",
  "estimatedTime": "rough time estimate"
}""")
            appendLine("```")
        }
    }
}
