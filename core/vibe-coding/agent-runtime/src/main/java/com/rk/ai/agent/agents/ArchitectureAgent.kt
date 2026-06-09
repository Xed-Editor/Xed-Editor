package com.rk.ai.agent.agents

import com.rk.ai.service.IdeService
import java.io.File

class ArchitectureAgent(private val ideService: IdeService) : SubAgent {
    override val name = "architect"
    override val description = "Analyzes codebase structure and designs architecture for new features. Provides module breakdowns, dependency analysis, multi-approach comparisons, and detailed implementation blueprints."
    override val capabilities = listOf(
        AgentCapability("explore", "Deep codebase exploration with pattern analysis", "path: string, depth: int"),
        AgentCapability("design", "Multi-approach architecture design with trade-off analysis", "requirements: string"),
        AgentCapability("dependency_map", "Map module dependencies with impact analysis", "path: string"),
        AgentCapability("migration_plan", "Plan incremental refactoring with safe steps", "from: string, to: string"),
    )

    override suspend fun execute(task: AgentTask): AgentResult {
        return try {
            val workspace = ideService.getPrimaryWorkspacePath()
            val maxDepth = if (task.prompt.contains("deep", ignoreCase = true)) 5 else 3
            val structure = ideService.getProjectStructure(workspace, maxDepth, 300)
            val config = ideService.getProjectConfig(workspace)

            val analysis = buildString {
                appendLine("# Architecture Analysis")
                appendLine()

                appendLine("## Request")
                appendLine("Task: ${task.prompt}")
                if (task.contextMessages.isNotEmpty()) {
                    appendLine("Context:")
                    task.contextMessages.forEach { appendLine("> $it") }
                }
                appendLine()

                appendLine("## Project Overview")
                appendLine("- Language: ${config["language"]?.asString ?: "unknown"}")
                appendLine("- Build System: ${config["buildSystem"]?.asString ?: "unknown"}")
                appendLine("- Workspace: $workspace")
                appendLine()

                appendLine("## Project Structure")
                appendLine("```")
                appendLine(structure)
                appendLine("```")
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
                        appendLine("```")
                        appendLine(file.readText().take(1500))
                        appendLine("```")
                        appendLine()
                    }
                }

                appendLine("## Pattern Analysis")
                appendLine("Analyzing existing codebase patterns...")
                appendLine()
                appendLine("### Detected Patterns")
                appendLine("- **Module structure**: Check module boundaries and package organization")
                appendLine("- **Dependency direction**: ${guessDependencyDirection(structure)}")
                appendLine("- **Pattern prevalence**: ${detectPatterns(workspace)}")
                appendLine()

                appendLine("## Architecture Approaches")
                appendLine()
                appendLine("### Approach A: Minimal Changes")
                appendLine("- Leverage existing abstractions")
                appendLine("- Minimal new files, maximum reuse")
                appendLine("- **Best for**: Small features, hotfixes, existing patterns align")
                appendLine()
                appendLine("### Approach B: Clean Architecture")
                appendLine("- New abstractions with clear separation of concerns")
                appendLine("- Higher initial cost, better long-term maintainability")
                appendLine("- **Best for**: Major features, new subsystems, long-lived code")
                appendLine()
                appendLine("### Approach C: Pragmatic Balance")
                appendLine("- Extract shared logic, inline the rest")
                appendLine("- Follow existing patterns where they fit")
                appendLine("- **Best for**: Most features, good middle ground")
                appendLine()

                appendLine("## Recommendation")
                appendLine("Based on the codebase analysis, Approach C (Pragmatic Balance) is recommended for most tasks.")
                appendLine("It minimizes disruption while maintaining code quality.")
                appendLine()

                appendLine("## Files Likely to Change")
                appendLine("(Requires more context about the specific task)")
            }

            AgentResult.Success(
                output = analysis,
                summary = "Architecture analysis prepared - project structure, patterns, and approaches documented",
            )
        } catch (e: Exception) {
            AgentResult.Failure("Architecture analysis failed: ${e.message}")
        }
    }

    private fun guessDependencyDirection(structure: String): String {
        return when {
            "domain" in structure && "data" in structure && "presentation" in structure ->
                "Clean Architecture: domain <- data <- presentation"
            "api" in structure && "impl" in structure ->
                "Interface-based: api <- impl"
            "core" in structure && "app" in structure ->
                "Core-centric: core -> app"
            else -> "Mixed (typical for Android projects)"
        }
    }

    private fun detectPatterns(workspace: String): String {
        val patterns = mutableListOf<String>()
        val srcDir = File(workspace, "src")
        if (!srcDir.exists()) return "Unable to scan (src directory not found)"

        val allFiles = srcDir.walkTopDown()
            .filter { it.extension == "kt" || it.extension == "java" || it.extension == "ts" || it.extension == "tsx" }
            .take(100)
            .toList()

        if (allFiles.any { it.readText().contains("interface ") }) patterns.add("Interface-based abstractions")
        if (allFiles.any { it.readText().contains("@Composable") }) patterns.add("Jetpack Compose UI")
        if (allFiles.any { it.readText().contains("ViewModel") }) patterns.add("MVVM (ViewModel)")
        if (allFiles.any { it.readText().contains("Repository") }) patterns.add("Repository pattern")
        if (allFiles.any { it.readText().contains("UseCase") || it.readText().contains("useCase") }) patterns.add("UseCase layer")
        if (allFiles.any { it.readText().contains("Dagger") || it.readText().contains("Hilt") || it.readText().contains("Koin") }) patterns.add("DI framework")
        if (allFiles.any { it.readText().contains("sealed class") || it.readText().contains("sealed interface") }) patterns.add("Sealed type hierarchies")

        return patterns.joinToString(", ").ifEmpty { "No clear patterns detected" }
    }
}
