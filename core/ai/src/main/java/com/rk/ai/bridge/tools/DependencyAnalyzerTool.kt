package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.File

class DependencyAnalyzerTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Code Intelligence"
    override fun getName(): String = "analyzeDependencies"
    override fun getDescription(): String = """Analyzes project dependencies and code relationships.
Identifies imports, module dependencies, and potential issues."""

    override fun getRequiredParams(): Map<String, String> = mapOf("action" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "target" to "string",
        "depth" to "number",
        "includeExternal" to "boolean"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "action" to "Action: 'imports', 'modules', 'circular', 'unused', 'graph'"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "target" to "File path or module to analyze (default: entire project)",
        "depth" to "Analysis depth (default: 3)",
        "includeExternal" to "Include external dependencies (default: true)"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val action = requireString(args, "action")
        val target = optionalString(args, "target")
        val depth = optionalInt(args, "depth") ?: 3
        val includeExternal = optionalBoolean(args, "includeExternal", true)

        val workspacePath = context.ideService.getPrimaryWorkspacePath()

        return when (action.lowercase()) {
            "imports" -> analyzeImports(context, target, workspacePath)
            "modules" -> analyzeModules(context, workspacePath, depth)
            "circular" -> detectCircularDeps(context, workspacePath)
            "unused" -> findUnusedDeps(context, workspacePath)
            "graph" -> buildDependencyGraph(context, workspacePath, depth)
            else -> McpToolResult.error("Unknown action: $action. Use: imports, modules, circular, unused, graph")
        }
    }

    private suspend fun analyzeImports(context: McpToolContext, target: String?, workspacePath: String): McpToolResult {
        val content = if (target != null) {
            val file = resolvePathOrThrow(context, target)
            context.ideService.getFileContent(file.absolutePath, null, null)
                ?: return McpToolResult.error("Could not read file: $target")
        } else {
            context.ideService.getOpenFiles()
                ?: return McpToolResult.error("No files to analyze")
        }

        return McpToolResult.success(
            buildString {
                appendLine("## Import Analysis")
                appendLine("**Target:** ${target ?: "open files"}")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Analyze the imports in the provided code and:")
                appendLine("1. Categorize imports: standard library, third-party, internal")
                appendLine("2. Identify unused imports")
                appendLine("3. Detect potential circular dependencies")
                appendLine("4. Suggest import optimizations")
                appendLine()
                appendLine("### Code to Analyze:")
                appendLine("```")
                appendLine(content.take(50000))
                appendLine("```")
            },
            mapOf("target" to (target ?: "open files"))
        )
    }

    private suspend fun analyzeModules(context: McpToolContext, workspacePath: String, depth: Int): McpToolResult {
        val structure = context.ideService.getProjectStructure(workspacePath)
            ?: return McpToolResult.error("Could not get project structure")

        return McpToolResult.success(
            buildString {
                appendLine("## Module Analysis")
                appendLine("**Workspace:** $workspacePath")
                appendLine("**Depth:** $depth")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Analyze the project structure and:")
                appendLine("1. Identify modules/packages")
                appendLine("2. Map module relationships")
                appendLine("3. Identify module responsibilities")
                appendLine("4. Detect potential module boundaries")
                appendLine()
                appendLine("### Project Structure:")
                appendLine(structure.take(20000))
            },
            mapOf("workspacePath" to workspacePath, "depth" to depth)
        )
    }

    private suspend fun detectCircularDeps(context: McpToolContext, workspacePath: String): McpToolResult {
        val structure = context.ideService.getProjectStructure(workspacePath)
            ?: return McpToolResult.error("Could not get project structure")

        return McpToolResult.success(
            buildString {
                appendLine("## Circular Dependency Detection")
                appendLine("**Workspace:** $workspacePath")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Analyze the project structure and:")
                appendLine("1. Identify potential circular dependencies")
                appendLine("2. Trace import chains")
                appendLine("3. Suggest breaking circular dependencies")
                appendLine("4. Recommend architectural improvements")
                appendLine()
                appendLine("### Project Structure:")
                appendLine(structure.take(20000))
            },
            mapOf("workspacePath" to workspacePath)
        )
    }

    private suspend fun findUnusedDeps(context: McpToolContext, workspacePath: String): McpToolResult {
        val structure = context.ideService.getProjectStructure(workspacePath)
            ?: return McpToolResult.error("Could not get project structure")
        val config = context.ideService.getProjectConfig()

        return McpToolResult.success(
            buildString {
                appendLine("## Unused Dependency Detection")
                appendLine("**Workspace:** $workspacePath")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Analyze the project and:")
                appendLine("1. Identify declared but unused dependencies")
                appendLine("2. Find used but undeclared dependencies")
                appendLine("3. Suggest dependency cleanup")
                appendLine("4. Recommend dependency updates")
                appendLine()
                if (!config.isNullOrBlank()) {
                    appendLine("### Project Config:")
                    appendLine(config.take(5000))
                }
                appendLine()
                appendLine("### Project Structure:")
                appendLine(structure.take(10000))
            },
            mapOf("workspacePath" to workspacePath)
        )
    }

    private suspend fun buildDependencyGraph(context: McpToolContext, workspacePath: String, depth: Int): McpToolResult {
        val structure = context.ideService.getProjectStructure(workspacePath)
            ?: return McpToolResult.error("Could not get project structure")

        return McpToolResult.success(
            buildString {
                appendLine("## Dependency Graph")
                appendLine("**Workspace:** $workspacePath")
                appendLine("**Depth:** $depth")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Build a dependency graph showing:")
                appendLine("1. Module dependencies (who depends on whom)")
                appendLine("2. External library usage")
                appendLine("3. Internal package relationships")
                appendLine("4. Critical path analysis")
                appendLine()
                appendLine("### Project Structure:")
                appendLine(structure.take(20000))
            },
            mapOf("workspacePath" to workspacePath, "depth" to depth)
        )
    }
}
