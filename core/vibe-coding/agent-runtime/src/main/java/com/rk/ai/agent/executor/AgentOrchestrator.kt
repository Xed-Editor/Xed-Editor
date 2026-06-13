package com.rk.ai.agent.executor

import android.util.Log
import com.rk.ai.agent.context.ContextMemoryManager
import com.rk.ai.agent.indexer.ProjectIndexer
import com.rk.ai.agent.planner.TaskPlanner
import com.rk.ai.agent.planner.TaskStatus
import com.rk.ai.agent.planner.TaskTree
import com.rk.ai.agent.tools.ToolCache
import com.rk.ai.agent.tools.ToolRouter
import kotlinx.coroutines.delay
import com.rk.ai.service.IdeService

private const val TAG = "AgentOrchestrator"

enum class AgentPhase {
    IDLE, PLANNING, ANALYZING, INDEXING, EXPLORING,
    EXECUTING, VERIFYING, REVIEWING, TESTING, COMPLETED, FAILED
}

data class OrchestratorResult(
    val success: Boolean,
    val summary: String = "",
    val taskTree: TaskTree? = null,
    val modifiedFiles: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val durationMs: Long = 0,
    val phase: AgentPhase = AgentPhase.COMPLETED,
)

class AgentOrchestrator(
    private val ideService: IdeService,
    private val contextMemory: ContextMemoryManager,
    private val toolCache: ToolCache,
    private val toolRouter: ToolRouter,
    private val executionEngine: ExecutionEngine,
    private val taskPlanner: TaskPlanner = TaskPlanner(),
    private val projectIndexer: ProjectIndexer,
) {
    private var currentPhase: AgentPhase = AgentPhase.IDLE
    private var onPhaseChange: ((AgentPhase) -> Unit)? = null

    fun setPhaseChangeListener(listener: (AgentPhase) -> Unit) {
        onPhaseChange = listener
    }

    fun getPhase(): AgentPhase = currentPhase

    suspend fun execute(goal: String): OrchestratorResult {
        val startTime = System.currentTimeMillis()
        val allModifiedFiles = mutableListOf<String>()
        val allErrors = mutableListOf<String>()

        try {
            // Phase 1: Index project
            setPhase(AgentPhase.INDEXING)
            val workspace = ideService.getPrimaryWorkspacePath()
            contextMemory.log("Starting orchestration for: $goal")
            contextMemory.log("Indexing project: $workspace")
            executionEngine.initialize(workspace)
            contextMemory.addFact("Project indexed for goal: $goal")

            // Phase 2: Analyze
            setPhase(AgentPhase.ANALYZING)
            contextMemory.log("Analyzing project structure")
            val summary = ideService.getProjectSummary(workspace)
            val structure = ideService.getProjectStructure(workspace, 3, 200)
            contextMemory.storeProjectInfo(summary, structure)
            contextMemory.addFact("Project analyzed: ${summary.take(200)}")

            // Phase 3: Plan
            setPhase(AgentPhase.PLANNING)
            contextMemory.log("Creating execution plan")
            var taskTree = taskPlanner.createPlan(goal, null)
            contextMemory.working.setTaskTree(taskTree)
            contextMemory.addFact("Plan created with ${taskTree.totalCount} steps")

            // Phase 4: Execute each task
            setPhase(AgentPhase.EXECUTING)
            var taskResult: ExecutionResult
            do {
                val nextTask = taskTree.nextExecutable() ?: break
                contextMemory.log("Executing: ${nextTask.title}")
                contextMemory.working.setCurrentTask(nextTask)

                taskTree = taskTree.updateTask(nextTask.id) { copy(status = TaskStatus.IN_PROGRESS) }
                contextMemory.working.setTaskTree(taskTree)

                taskResult = executionEngine.executeTask(
                    task = nextTask,
                    tools = emptyList(),
                    generateWithLLM = { _, _, _ -> "" },
                )

                if (taskResult.success) {
                    taskTree = taskTree.updateTask(nextTask.id) {
                        copy(status = TaskStatus.COMPLETED, result = taskResult.message)
                    }
                    contextMemory.working.setTaskTree(taskTree)
                    allModifiedFiles.addAll(taskResult.modifiedFiles)
                    contextMemory.log("Completed: ${nextTask.title}")
                } else {
                    taskTree = taskTree.updateTask(nextTask.id) {
                        copy(status = TaskStatus.FAILED, error = taskResult.errors.joinToString(", "))
                    }
                    contextMemory.working.setTaskTree(taskTree)
                    allErrors.addAll(taskResult.errors)
                    contextMemory.log("Failed: ${nextTask.title}")
                }

                if (allErrors.size > 5) {
                    contextMemory.log("Too many errors, aborting")
                    break
                }
            } while (!taskTree.isComplete)

            // Phase 5: Verify
            if (allErrors.isEmpty()) {
                setPhase(AgentPhase.VERIFYING)
                contextMemory.log("Verifying changes")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Orchestration failed", e)
            allErrors.add("Fatal: ${e.message}")
            setPhase(AgentPhase.FAILED)
        }

        val duration = System.currentTimeMillis() - startTime
        val success = allErrors.isEmpty()

        if (success) {
            setPhase(AgentPhase.COMPLETED)
            contextMemory.log("Goal completed in ${duration}ms")
        } else {
            setPhase(AgentPhase.FAILED)
            contextMemory.log("Goal failed after ${duration}ms: ${allErrors.size} errors")
        }

        return OrchestratorResult(
            success = success,
            summary = buildString {
                appendLine(if (success) "Goal completed successfully" else "Goal failed")
                appendLine("Duration: ${duration}ms")
                if (allModifiedFiles.isNotEmpty()) {
                    appendLine("Modified files:")
                    allModifiedFiles.distinct().forEach { appendLine("  - $it") }
                }
            },
            modifiedFiles = allModifiedFiles.distinct(),
            errors = allErrors,
            durationMs = duration,
            phase = currentPhase,
        )
    }

    fun stop() {
        setPhase(AgentPhase.IDLE)
        contextMemory.log("Orchestration stopped by user")
    }

    private fun setPhase(phase: AgentPhase) {
        currentPhase = phase
        onPhaseChange?.invoke(phase)
    }
}
