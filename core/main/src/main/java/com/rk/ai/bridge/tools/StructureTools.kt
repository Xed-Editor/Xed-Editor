package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class GetProjectStructureTool : BaseMcpTool() {
    override fun getName(): String = "getProjectStructure"
    override fun getDescription(): String = "Returns a hierarchical project directory tree."
    override fun getRequiredParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("maxDepth" to "number", "maxItems" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Directory path to explore"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "maxDepth" to "Maximum directory depth (default: 3, max: 10)",
        "maxItems" to "Maximum items to return (default: 200, max: 1000)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val path = optionalString(args, "path").ifBlank { context.ideService.getPrimaryWorkspacePath() }
        val maxDepth = (optionalPositiveInt(args, "maxDepth") ?: 3).coerceIn(1, 10)
        val maxItems = (optionalPositiveInt(args, "maxItems") ?: 200).coerceIn(1, 1000)
        val tree = context.ideService.getProjectStructure(path, maxDepth, maxItems)
        return McpToolResult.success(tree)
    }
}

class GetProjectSummaryTool : BaseMcpTool() {
    override fun getName(): String = "getProjectSummary"
    override fun getDescription(): String = "CRITICAL: Use this as your FIRST tool call to get a high-level overview of the project (README, build files, config, open tabs, and git status)."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val ideService = context.ideService
        val rootPath = ideService.getPrimaryWorkspacePath()
        if (rootPath.isBlank()) return McpToolResult.success("No workspace open.")
        val root = java.io.File(rootPath)
        val config = ideService.getProjectConfig(rootPath)
        val sb = StringBuilder()
        sb.append("╔══════════════════════════════════════════════╗\n")
        sb.append("║  USE NATIVE MCP TOOLS INSTEAD OF TERMINAL   ║\n")
        sb.append("║  cat→readFile  grep→searchCode  find→glob  ║\n")
        sb.append("║  ls→listFiles  wc→wc  head→head  tail→tail ║\n")
        sb.append("║  stat→stat  wc -l→countLines               ║\n")
        sb.append("╚══════════════════════════════════════════════╝\n\n")
        sb.append("Project Root: ").append(rootPath).append("\n")
        sb.append("Language: ").append(config.get("language")?.asString ?: "unknown").append("\n")
        sb.append("Build System: ").append(config.get("buildSystem")?.asString ?: "unknown").append("\n")

        val status = ideService.getGitStatus(rootPath)
        if (status.has("branch")) {
            sb.append("Git Branch: ").append(status.get("branch").asString).append("\n")
            sb.append("Total Changes: ").append(status.get("totalChanges").asInt).append("\n")
        }

        val openFiles = ideService.getOpenFiles()
        if (openFiles.isNotEmpty()) {
            sb.append("\nOpen Tabs:\n")
            openFiles.take(10).forEach { file ->
                sb.append(" - ").append(file.get("path").asString)
                if (file.get("isActive").asBoolean) sb.append(" [ACTIVE]")
                sb.append("\n")
            }
        }

        sb.append("\n")

        val importantFiles = listOf("README.md", "README", "GEMINI.md", "build.gradle.kts", "build.gradle", "package.json", "pom.xml", "Cargo.toml", "requirements.txt")
        importantFiles.forEach { name ->
            val file = java.io.File(root, name)
            if (file.exists() && file.isFile) {
                val content = runCatching {
                    val text = file.readText()
                    if (text.length > 2000) text.take(2000) + "... (truncated)" else text
                }.getOrDefault("")
                if (content.isNotBlank()) {
                    sb.append("--- ").append(name).append(" ---\n")
                    sb.append(content).append("\n\n")
                }
            }
        }
        return McpToolResult.success(sb.toString())
    }
}
