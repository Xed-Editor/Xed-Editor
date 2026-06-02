package com.rk.ai.agent.agents

import com.rk.ai.service.IdeService
import java.io.File

class ArchitectureAgent(private val ideService: IdeService) : SubAgent {
    override val name = "architect"
    override val description = "Analyzes codebase structure and designs architecture for new features. Provides module breakdowns, dependency analysis, and implementation blueprints."
    override val capabilities = listOf(
        AgentCapability("explore", "Explore codebase structure and patterns", "path: string, depth: int"),
        AgentCapability("design", "Design architecture for new features", "requirements: string"),
        AgentCapability("dependency_map", "Map module dependencies", "path: string"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val maxDepth = if (task.prompt.contains("deep", ignoreCase = true)) 5 else 3
            val structure = ideService.getProjectStructure(workspace, maxDepth, 300)
            val config = ideService.getProjectConfig(workspace)

            val analysis = buildString {
                appendLine("## Architecture Analysis Request")
                appendLine()
                appendLine("Task: ${task.prompt}")
                if (task.contextMessages.isNotEmpty()) {
                    appendLine()
                    appendLine("Context:")
                    task.contextMessages.forEach { appendLine(it) }
                }
                appendLine()

                appendLine("## Project Overview")
                appendLine("Language: ${config["language"]?.asString ?: "unknown"}")
                appendLine("Build System: ${config["buildSystem"]?.asString ?: "unknown"}")
                appendLine("Workspace: $workspace")
                appendLine()

                appendLine("## Project Structure")
                appendLine(structure)
                appendLine()

                val keyFiles = listOf(
                    File(workspace, "build.gradle.kts"),
                    File(workspace, "build.gradle"),
                    File(workspace, "settings.gradle.kts"),
                    File(workspace, "package.json"),
                    File(workspace, "Cargo.toml"),
                    File(workspace, "pom.xml"),
                ).filter { it.exists() }

                if (keyFiles.any()) {
                    appendLine("## Build Configuration")
                    keyFiles.forEach { file ->
                        appendLine("### ${file.name}")
                        appendLine(file.readText().take(2000))
                        appendLine()
                    }
                }
            }

            AgentResult.Success(
                output = analysis,
                summary = "Architecture analysis prepared - project structure and config analyzed",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Architecture analysis failed: ${e.message}")
        }
    }
}
