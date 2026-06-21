package com.rk.ai.agent.planner

import com.rk.ai.agent.indexer.ProjectKnowledgeBase

data class TaskAnalysis(
    val isComplex: Boolean,
    val riskLevel: RiskLevel,
    val suggestedDepth: Int,
    val requiresBuild: Boolean,
    val requiresTest: Boolean,
    val estimatedFiles: Int,
)

enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

class TaskPlanner {

    fun analyzeTask(goal: String, knowledgeBase: ProjectKnowledgeBase?): TaskAnalysis {
        val lowRiskKeywords = listOf("typo", "rename", "comment", "doc", "format", "refactor", "style")
        val highRiskKeywords = listOf("security", "auth", "payment", "database", "migration", "api", "network")
        val criticalKeywords = listOf("delete", "remove", "migrate", "rewrite", "restructure", "change architecture")

        val hasLowRisk = lowRiskKeywords.any { goal.contains(it, ignoreCase = true) }
        val hasHighRisk = highRiskKeywords.any { goal.contains(it, ignoreCase = true) }
        val hasCriticalRisk = criticalKeywords.any { goal.contains(it, ignoreCase = true) }

        val hasBuild = goal.contains("build", ignoreCase = true) ||
            goal.contains("compile", ignoreCase = true) ||
            goal.contains("ci", ignoreCase = true)

        val hasTest = goal.contains("test", ignoreCase = true) ||
            goal.contains("coverage", ignoreCase = true)

        val isComplex = goal.length > 40 ||
            goal.contains("implement", ignoreCase = true) ||
            goal.contains("build", ignoreCase = true) ||
            goal.contains("refactor", ignoreCase = true) ||
            goal.contains("create", ignoreCase = true) ||
            goal.contains("add", ignoreCase = true) ||
            goal.contains("feature", ignoreCase = true)

        val riskLevel = when {
            hasCriticalRisk -> RiskLevel.CRITICAL
            hasHighRisk -> RiskLevel.HIGH
            hasLowRisk -> RiskLevel.LOW
            isComplex -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val estimatedFiles = when {
            isComplex && hasCriticalRisk -> 10
            isComplex && hasHighRisk -> 6
            isComplex -> 4
            hasTest -> 2
            else -> 1
        }

        return TaskAnalysis(
            isComplex = isComplex,
            riskLevel = riskLevel,
            suggestedDepth = if (isComplex) 5 else 2,
            requiresBuild = hasBuild || isComplex,
            requiresTest = hasTest || isComplex,
            estimatedFiles = estimatedFiles,
        )
    }

    fun createPlan(goal: String, knowledgeBase: ProjectKnowledgeBase?): TaskTree {
        val analysis = analyzeTask(goal, knowledgeBase)

        if (!analysis.isComplex && analysis.riskLevel == RiskLevel.LOW) {
            return TaskTree(
                goal = goal,
                riskLevel = analysis.riskLevel,
                rootTasks = listOf(
                    TaskNode(
                        title = goal,
                        description = "Direct execution",
                        estimatedFiles = analysis.estimatedFiles,
                        requiresBuild = analysis.requiresBuild,
                        requiresTest = analysis.requiresTest,
                    ),
                ),
            )
        }

        val relevantFiles = knowledgeBase?.findFilesRelevantTo(goal) ?: emptyList()

        val explorePhase = TaskNode(
            id = "phase-explore",
            title = "Explore codebase",
            description = if (relevantFiles.isNotEmpty()) {
                "Read relevant files and understand current implementation in ${relevantFiles.size} files"
            } else {
                "Read relevant files and understand current implementation"
            },
            estimatedFiles = relevantFiles.size.coerceAtMost(5),
            subtasks = buildList {
                if (knowledgeBase != null) {
                    add(
                        TaskNode(
                            title = "Read project structure",
                            description = "Understand module layout and dependencies",
                        )
                    )
                    val targetFiles = relevantFiles.take(5)
                    targetFiles.forEach { file ->
                        add(
                            TaskNode(
                                title = "Read ${file.split("/").last()}",
                                description = "Understand current implementation in $file",
                            )
                        )
                    }
                }
            },
        )

        val planPhase = TaskNode(
            id = "phase-plan",
            title = "Design solution",
            description = "Plan the implementation approach considering architecture and edge cases",
            dependencies = listOf("phase-explore"),
            riskLevel = analysis.riskLevel,
            subtasks = listOf(
                TaskNode(
                    title = "Analyze requirements",
                    description = "Understand what needs to change and identify edge cases",
                ),
                TaskNode(
                    title = "Design approach",
                    description = "Plan minimal changes that achieve the goal without breaking existing code",
                ),
            ),
        )

        val implementationSubtasks = mutableListOf<TaskNode>()

        if (relevantFiles.isNotEmpty()) {
            relevantFiles.take(analysis.estimatedFiles.coerceAtMost(8)).forEach { file ->
                implementationSubtasks.add(
                    TaskNode(
                        title = "Modify ${file.split("/").last()}",
                        description = "Update $file with the required changes",
                    )
                )
            }
        } else {
            implementationSubtasks.add(
                TaskNode(
                    title = "Make code changes",
                    description = "Write or modify files to implement the solution",
                )
            )
        }

        val implPhase = TaskNode(
            id = "phase-implement",
            title = "Implement changes",
            description = "Write the actual code changes across ${implementationSubtasks.size} file(s)",
            dependencies = listOf("phase-plan"),
            estimatedFiles = implementationSubtasks.size,
            requiresBuild = analysis.requiresBuild,
            subtasks = implementationSubtasks,
        )

        val verifyPhase = TaskNode(
            id = "phase-verify",
            title = "Verify changes",
            description = "Check diagnostics, run tests, and validate correctness",
            dependencies = listOf("phase-implement"),
            requiresBuild = analysis.requiresBuild,
            requiresTest = analysis.requiresTest,
            subtasks = buildList {
                add(
                    TaskNode(
                        title = "Check diagnostics",
                        description = "Run LSP diagnostics on ALL changed files",
                    )
                )
                if (analysis.requiresBuild) {
                    add(
                        TaskNode(
                            title = "Build project",
                            description = "Run build command to verify compilation",
                        )
                    )
                }
                if (analysis.requiresTest) {
                    add(
                        TaskNode(
                            title = "Run tests",
                            description = "Execute relevant tests to verify correctness",
                        )
                    )
                }
                add(
                    TaskNode(
                        title = "Fix any issues",
                        description = "Resolve warnings, errors, or test failures",
                    )
                )
            },
        )

        val reviewPhase = TaskNode(
            id = "phase-review",
            title = "Review and finalize",
            description = if (analysis.riskLevel.ordinal >= RiskLevel.HIGH.ordinal) {
                "CRITICAL: Self-review all changes for correctness, edge cases, and regressions"
            } else {
                "Self-review all changes for consistency and correctness"
            },
            dependencies = listOf("phase-verify"),
            riskLevel = analysis.riskLevel,
            subtasks = when {
                analysis.riskLevel.ordinal >= RiskLevel.HIGH.ordinal -> listOf(
                    TaskNode(title = "Edge case audit", description = "Check null safety, error handling, boundary conditions"),
                    TaskNode(title = "Regression check", description = "Verify existing behavior is unchanged"),
                    TaskNode(title = "Code quality review", description = "Check consistency, naming, dead code"),
                )
                else -> listOf(
                    TaskNode(title = "Final review", description = "Check changes for correctness and consistency"),
                )
            },
        )

        return TaskTree(
            goal = goal,
            rootTasks = listOf(explorePhase, planPhase, implPhase, verifyPhase, reviewPhase),
            riskLevel = analysis.riskLevel,
        )
    }

    fun createSubtask(parentId: String, description: String): TaskNode {
        return TaskNode(
            title = description.take(60),
            description = description,
            dependencies = listOf(parentId),
        )
    }
}
