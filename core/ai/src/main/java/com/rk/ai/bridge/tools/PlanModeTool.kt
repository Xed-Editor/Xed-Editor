package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class PlanModeTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Planning"
    override fun getName(): String = "planMode"
    override fun getDescription(): String = """Activates plan mode for the AI agent to create and approve execution plans."""

    override fun getRequiredParams(): Map<String, String> = mapOf("action" to "string", "task" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "context" to "string",
        "maxSteps" to "number",
        "requireApproval" to "boolean"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "action" to "Action: 'start', 'approve', 'reject', 'status'",
        "task" to "Description of the task to plan"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "context" to "Additional context for the plan",
        "maxSteps" to "Maximum number of steps (default: 15)",
        "requireApproval" to "Require user approval before execution (default: true)"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val action = requireString(args, "action")
        val task = requireString(args, "task")
        val taskContext = optionalString(args, "context")
        val maxSteps = optionalInt(args, "maxSteps") ?: 15
        val requireApproval = optionalBoolean(args, "requireApproval", true)

        return when (action.lowercase()) {
            "start" -> startPlanMode(context, task, taskContext, maxSteps)
            "approve" -> approvePlan(task)
            "reject" -> rejectPlan(task)
            "status" -> getPlanStatus()
            else -> McpToolResult.error("Unknown action: $action. Use: start, approve, reject, status")
        }
    }

    private suspend fun startPlanMode(context: McpToolContext, task: String, taskContext: String?, maxSteps: Int): McpToolResult {
        val workspacePath = context.ideService.getPrimaryWorkspacePath()
        val projectStructure = context.ideService.getProjectStructure(workspacePath, 3, 200)
        val openFiles = context.ideService.getOpenFiles()

        return McpToolResult.success(
            buildString {
                appendLine("## PLAN MODE ACTIVATED")
                appendLine()
                appendLine("### Phase 1: Analysis")
                appendLine("1. Read and understand the task completely")
                appendLine("2. Gather all relevant context")
                appendLine("3. Identify potential risks and edge cases")
                appendLine()
                appendLine("### Phase 2: Planning")
                appendLine("1. Break the task into $maxSteps or fewer clear steps")
                appendLine("2. Each step should be independently testable")
                appendLine("3. Identify dependencies between steps")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Task to Plan:")
                appendLine(task)

                if (!taskContext.isNullOrBlank()) {
                    appendLine()
                    appendLine("### Additional Context:")
                    appendLine(taskContext)
                }

                if (projectStructure.isNotBlank()) {
                    appendLine()
                    appendLine("### Project Structure:")
                    appendLine(projectStructure.take(5000))
                }

                if (openFiles.isNotEmpty()) {
                    appendLine()
                    appendLine("### Open Files:")
                    openFiles.take(5).forEach { file ->
                        appendLine("- ${file.get("filePath")?.asString ?: file.toString()}")
                    }
                }

                appendLine()
                appendLine("**IMPORTANT:** Present the plan first. Do NOT execute any changes until approved.")
            },
            emptyMap()
        )
    }

    private suspend fun approvePlan(task: String): McpToolResult = McpToolResult.success(
        buildString {
            appendLine("## PLAN APPROVED")
            appendLine()
            appendLine("You may now execute the steps one at a time.")
            appendLine()
            appendLine("### Task:")
            appendLine(task)
        },
        emptyMap()
    )

    private suspend fun rejectPlan(task: String): McpToolResult = McpToolResult.success(
        buildString {
            appendLine("## PLAN REJECTED")
            appendLine()
            appendLine("Please review feedback and create a new plan.")
            appendLine()
            appendLine("### Task:")
            appendLine(task)
        },
        emptyMap()
    )

    private suspend fun getPlanStatus(): McpToolResult = McpToolResult.success(
        buildString {
            appendLine("## Plan Mode Status")
            appendLine()
            appendLine("Available actions: start, approve, reject, status")
        }
    )
}
