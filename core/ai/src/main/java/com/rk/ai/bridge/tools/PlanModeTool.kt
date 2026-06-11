package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class PlanModeTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Planning"
    override fun getName(): String = "planMode"
    override fun getDescription(): String = """Activates plan mode for the AI agent. In plan mode, the agent must:
1. Analyze the task thoroughly
2. Create a detailed execution plan
3. Present the plan for user approval
4. Only execute after approval

This prevents accidental changes and ensures careful consideration."""

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
            "start" -> startPlanMode(context, task, taskContext, maxSteps, requireApproval)
            "approve" -> approvePlan(context, task)
            "reject" -> rejectPlan(context, task)
            "status" -> getPlanStatus(context)
            else -> McpToolResult.error("Unknown action: $action. Use: start, approve, reject, status")
        }
    }

    private suspend fun startPlanMode(
        context: McpToolContext,
        task: String,
        taskContext: String?,
        maxSteps: Int,
        requireApproval: Boolean
    ): McpToolResult {
        val workspacePath = context.ideService.getPrimaryWorkspacePath()
        val projectStructure = context.ideService.getProjectStructure(workspacePath, 3, 200)
        val openFiles = context.ideService.getOpenFiles()
        val projectConfig = context.ideService.getProjectConfig(workspacePath)
        val activeFile = context.ideService.getActiveFile()
        val diagnostics = if (activeFile != null) {
            val filePath = activeFile.get("filePath")?.asString
            if (filePath != null) context.ideService.getDiagnostics(filePath) else null
        } else null

        return McpToolResult.success(
            buildString {
                appendLine("## PLAN MODE ACTIVATED")
                appendLine()
                appendLine("You are now in **PLAN MODE**. You MUST follow this workflow:")
                appendLine()
                appendLine("### Phase 1: Analysis")
                appendLine("1. Read and understand the task completely")
                appendLine("2. Gather all relevant context (files, dependencies, existing code)")
                appendLine("3. Identify potential risks and edge cases")
                appendLine()
                appendLine("### Phase 2: Planning")
                appendLine("1. Break the task into $maxSteps or fewer clear steps")
                appendLine("2. Each step should be independently testable")
                appendLine("3. Identify dependencies between steps")
                appendLine("4. Estimate complexity for each step")
                appendLine()
                appendLine("### Phase 3: Presentation")
                appendLine("1. Present the plan in a clear, structured format")
                appendLine("2. Explain your reasoning for each step")
                appendLine("3. Highlight any risks or alternatives")
                appendLine()
                appendLine("### Phase 4: Execution (After Approval)")
                appendLine("1. Only execute after user approval")
                appendLine("2. Execute one step at a time")
                appendLine("3. Verify each step before moving to the next")
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

                if (diagnostics != null && !diagnostics.isEmpty) {
                    appendLine()
                    appendLine("### Current Diagnostics:")
                    appendLine(diagnostics.toString().take(2000))
                }

                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Your Response Format:")
                appendLine("```json")
                appendLine("""{
  "plan": {
    "summary": "Brief overview of the approach",
    "steps": [
      {
        "id": 1,
        "title": "Step title",
        "description": "What to do",
        "files": ["files to modify"],
        "dependencies": [],
        "complexity": "low|medium|high",
        "testCriteria": "how to verify"
      }
    ],
    "risks": ["potential issues"],
    "alternatives": ["other approaches considered"]
  },
  "readyForApproval": true
}""")
                appendLine("```")
                appendLine()
                appendLine("**IMPORTANT:** Present the plan first. Do NOT execute any changes until approved.")
            },
            mapOf(
                "action" to "start",
                "task" to task,
                "maxSteps" to maxSteps,
                "requireApproval" to requireApproval
            )
        )
    }

    private suspend fun approvePlan(context: McpToolContext, task: String): McpToolResult {
        return McpToolResult.success(
            buildString {
                appendLine("## PLAN APPROVED")
                appendLine()
                appendLine("The plan has been approved. You may now execute the steps.")
                appendLine()
                appendLine("### Execution Guidelines:")
                appendLine("1. Execute one step at a time")
                appendLine("2. After each step, verify the result")
                appendLine("3. If a step fails, stop and report the issue")
                appendLine("4. Use appropriate tools for each step")
                appendLine("5. Track progress and update status")
                appendLine()
                appendLine("### Task:")
                appendLine(task)
            },
            mapOf("action" to "approve", "task" to task)
        )
    }

    private suspend fun rejectPlan(context: McpToolContext, task: String): McpToolResult {
        return McpToolResult.success(
            buildString {
                appendLine("## PLAN REJECTED")
                appendLine()
                appendLine("The plan has been rejected. Please:")
                appendLine("1. Review the feedback")
                appendLine("2. Consider alternative approaches")
                appendLine("3. Create a new plan addressing the concerns")
                appendLine()
                appendLine("### Task:")
                appendLine(task)
                appendLine()
                appendLine("Ask the user for feedback on what should be changed.")
            },
            mapOf("action" to "reject", "task" to task)
        )
    }

    private suspend fun getPlanStatus(context: McpToolContext): McpToolResult {
        return McpToolResult.success(
            buildString {
                appendLine("## Plan Mode Status")
                appendLine()
                appendLine("Plan mode allows you to:")
                appendLine("- Create structured execution plans")
                appendLine("- Get user approval before making changes")
                appendLine("- Track progress through steps")
                appendLine("- Handle failures gracefully")
                appendLine()
                appendLine("### Available Actions:")
                appendLine("- `start` - Begin planning a task")
                appendLine("- `approve` - Approve a plan for execution")
                appendLine("- `reject` - Reject a plan and request changes")
                appendLine("- `status` - Show this help")
            }
        )
    }
}
