package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class DependencyAnalyzerTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Code Intelligence"
    override fun getName(): String = "analyzeDependencies"
    override fun getDescription(): String = """Analyzes project dependencies and code relationships."""

    override fun getRequiredParams(): Map<String, String> = mapOf("action" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "target" to "string",
        "depth" to "number"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "action" to "Action: 'imports', 'modules', 'circular', 'unused', 'graph'"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "target" to "File path or module to analyze (default: open files)",
        "depth" to "Analysis depth (default: 3)"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val action = requireString(args, "action")
        val target = optionalString(args, "target")
        val depth = optionalInt(args, "depth") ?: 3
        val workspacePath = context.ideService.getPrimaryWorkspacePath()

        return when (action.lowercase()) {
            "imports" -> analyzeImports(context, target)
            "modules" -> analyzeModules(context, workspacePath, depth)
            "circular" -> detectCircularDeps(context, workspacePath)
            "unused" -> findUnusedDeps(context, workspacePath)
            "graph" -> buildDependencyGraph(context, workspacePath, depth)
            else -> McpToolResult.error("Unknown action: $action. Use: imports, modules, circular, unused, graph")
        }
    }

    private suspend fun analyzeImports(context: McpToolContext, target: String?): McpToolResult {
        val content = if (target != null) {
            val file = resolvePathOrThrow(context, target)
            context.ideService.getFileContent(file.absolutePath)
                ?: return McpToolResult.error("Could not read file: $target")
        } else {
            val files = context.ideService.getOpenFiles()
            if (files.isEmpty()) return McpToolResult.error("No files to analyze")
            files.joinToString("\n") { it.toString() }
        }

        return McpToolResult.success(
            buildString {
                appendLine("## Import Analysis")
                appendLine("**Target:** ${target ?: "open files"}")
                appendLine()
                appendLine("### Code to Analyze:")
                appendLine("```")
                appendLine(content.take(50000))
                appendLine("```")
            },
            emptyMap()
        )
    }

    private suspend fun analyzeModules(context: McpToolContext, workspacePath: String, depth: Int): McpToolResult {
        val structure = context.ideService.getProjectStructure(workspacePath, depth, 200)

        return McpToolResult.success(
            buildString {
                appendLine("## Module Analysis")
                appendLine("**Workspace:** $workspacePath")
                appendLine("**Depth:** $depth")
                appendLine()
                appendLine("### Project Structure:")
                appendLine(structure.take(20000))
            },
            emptyMap()
        )
    }

    private suspend fun detectCircularDeps(context: McpToolContext, workspacePath: String): McpToolResult {
        val structure = context.ideService.getProjectStructure(workspacePath, 5, 200)

        return McpToolResult.success(
            buildString {
                appendLine("## Circular Dependency Detection")
                appendLine("**Workspace:** $workspacePath")
                appendLine()
                appendLine("### Project Structure:")
                appendLine(structure.take(20000))
            },
            emptyMap()
        )
    }

    private suspend fun findUnusedDeps(context: McpToolContext, workspacePath: String): McpToolResult {
        val structure = context.ideService.getProjectStructure(workspacePath, 5, 200)
        val config = context.ideService.getProjectConfig(workspacePath)

        return McpToolResult.success(
            buildString {
                appendLine("## Unused Dependency Detection")
                appendLine("**Workspace:** $workspacePath")
                appendLine()
                val configStr = config.toString()
                if (configStr.isNotBlank() && configStr != "{}") {
                    appendLine("### Project Config:")
                    appendLine(configStr.take(5000))
                }
                appendLine()
                appendLine("### Project Structure:")
                appendLine(structure.take(10000))
            },
            emptyMap()
        )
    }

    private suspend fun buildDependencyGraph(context: McpToolContext, workspacePath: String, depth: Int): McpToolResult {
        val structure = context.ideService.getProjectStructure(workspacePath, depth, 200)

        return McpToolResult.success(
            buildString {
                appendLine("## Dependency Graph")
                appendLine("**Workspace:** $workspacePath")
                appendLine("**Depth:** $depth")
                appendLine()
                appendLine("### Project Structure:")
                appendLine(structure.take(20000))
            },
            emptyMap()
        )
    }
}
