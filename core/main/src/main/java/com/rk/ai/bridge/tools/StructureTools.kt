package com.rk.ai.bridge.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.File

class GetProjectStructureTool : BaseMcpTool() {
    override val name: String = "getProjectStructure"
    override val description: String = "Returns a hierarchical project directory tree."
    override val requiredParams: Map<String, String> = mapOf("path" to "string")
    override val optionalParams: Map<String, String> = mapOf("maxDepth" to "number", "maxItems" to "number")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val path = optionalString(args, "path").ifBlank { context.ideService.getPrimaryWorkspacePath() }
        val maxDepth = optionalInt(args, "maxDepth", 3).coerceIn(1, 10)
        val maxItems = optionalInt(args, "maxItems", 200).coerceIn(1, 1000)
        val tree = context.ideService.getProjectStructure(path, maxDepth, maxItems)
        return resultText(tree)
    }
}

class GetProjectSummaryTool : BaseMcpTool() {
    override val name: String = "getProjectSummary"
    override val description: String = "CRITICAL: Use this as your FIRST tool call to get a high-level overview of the project (README, build files, config, open tabs, and git status). It is much more efficient than getProjectStructure for initial orientation."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val rootPath = context.ideService.getPrimaryWorkspacePath()
        if (rootPath.isBlank()) return resultText("No workspace open.")
        val root = File(rootPath)
        val config = context.ideService.getProjectConfig(rootPath)
        val sb = StringBuilder()
        sb.append("Project Root: ").append(rootPath).append("\n")
        sb.append("Language: ").append(config.get("language")?.asString ?: "unknown").append("\n")
        sb.append("Build System: ").append(config.get("buildSystem")?.asString ?: "unknown").append("\n")

        val status = context.ideService.getGitStatus(rootPath)
        if (status.has("branch")) {
            sb.append("Git Branch: ").append(status.get("branch").asString).append("\n")
            sb.append("Total Changes: ").append(status.get("totalChanges").asInt).append("\n")
        }
        
        val openFiles = context.ideService.getOpenFiles()
        if (openFiles.isNotEmpty()) {
            sb.append("\nOpen Tabs:\n")
            openFiles.take(10).forEach { file ->
                sb.append(" - ").append(file.get("path").asString)
                if (file.get("isActive").asBoolean) sb.append(" [ACTIVE]")
                sb.append("\n")
            }
        }

        sb.append("\n")

        val importantFiles = listOf("README.md", "README", "build.gradle.kts", "build.gradle", "package.json", "pom.xml", "Cargo.toml", "requirements.txt")
        importantFiles.forEach { name ->
            val file = File(root, name)
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
        return resultText(sb.toString())
    }
}
