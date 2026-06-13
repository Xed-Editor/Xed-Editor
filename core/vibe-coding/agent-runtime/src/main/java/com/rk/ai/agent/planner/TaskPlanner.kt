package com.rk.ai.agent.planner

import com.rk.ai.agent.indexer.ProjectKnowledgeBase

class TaskPlanner {

    fun createPlan(goal: String, knowledgeBase: ProjectKnowledgeBase?): TaskTree {
        val isComplex = goal.length > 40 || goal.contains("implement", ignoreCase = true) ||
            goal.contains("build", ignoreCase = true) || goal.contains("refactor", ignoreCase = true) ||
            goal.contains("create", ignoreCase = true) || goal.contains("add", ignoreCase = true)

        if (!isComplex) {
            return TaskTree(
                goal = goal,
                rootTasks = listOf(
                    TaskNode(title = goal, description = "Direct execution")
                ),
            )
        }

        val steps = listOf(
            TaskNode(
                id = "phase-explore",
                title = "Explore codebase",
                description = "Read relevant files and understand current implementation",
                subtasks = listOf(
                    TaskNode(title = "Find related files", description = "Search for relevant code"),
                    TaskNode(title = "Read existing implementation", description = "Understand current code structure"),
                ),
            ),
            TaskNode(
                id = "phase-plan",
                title = "Design solution",
                description = "Plan the implementation approach",
                dependencies = listOf("phase-explore"),
            ),
            TaskNode(
                id = "phase-implement",
                title = "Implement changes",
                description = "Write the actual code changes",
                dependencies = listOf("phase-plan"),
                subtasks = buildList {
                    if (knowledgeBase != null) {
                        val fileTargets = knowledgeBase.findFilesRelevantTo(goal)
                        fileTargets.take(5).forEach { file ->
                            add(TaskNode(title = "Modify ${file.split("/").last()}", description = "Update $file"))
                        }
                    }
                    if (isEmpty()) add(TaskNode(title = "Make code changes", description = "Write or modify files"))
                },
            ),
            TaskNode(
                id = "phase-verify",
                title = "Verify changes",
                description = "Check diagnostics, run tests",
                dependencies = listOf("phase-implement"),
                subtasks = listOf(
                    TaskNode(title = "Check diagnostics", description = "Run LSP diagnostics on changed files"),
                    TaskNode(title = "Fix any issues", description = "Resolve compilation errors"),
                ),
            ),
            TaskNode(
                id = "phase-review",
                title = "Review and finalize",
                description = "Self-review all changes",
                dependencies = listOf("phase-verify"),
            ),
        )

        return TaskTree(goal = goal, rootTasks = steps)
    }

    fun createSubtask(parentId: String, description: String): TaskNode {
        return TaskNode(
            title = description.take(60),
            description = description,
            dependencies = listOf(parentId),
        )
    }
}
