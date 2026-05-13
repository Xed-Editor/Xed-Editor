package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GetProjectStructureTool : BaseMcpTool() {
    override fun getName(): String = "getProjectStructure"
    override fun getDescription(): String = "Returns a hierarchical project directory tree."
    override fun getRequiredParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("maxDepth" to "number", "maxItems" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val maxDepth = optionalInt(args, "maxDepth", 3).coerceIn(1, 10)
        val maxItems = optionalInt(args, "maxItems", 200).coerceIn(1, 1000)
        val tree = ideService.getProjectStructure(path, maxDepth, maxItems)
        return textResult(tree)
    }
}

class GetProjectSummaryTool : BaseMcpTool() {
    override fun getName(): String = "getProjectSummary"
    override fun getDescription(): String = "Returns a high-level summary of the project (README, build files, config)."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val rootPath = ideService.getPrimaryWorkspacePath()
        if (rootPath.isBlank()) return textResult("No workspace open.")
        val root = java.io.File(rootPath)
        val config = ideService.getProjectConfig(rootPath)
        val sb = StringBuilder()
        sb.append("Project Root: ").append(rootPath).append("\n")
        sb.append("Language: ").append(config.get("language")?.asString ?: "unknown").append("\n")
        sb.append("Build System: ").append(config.get("buildSystem")?.asString ?: "unknown").append("\n\n")

        val importantFiles = listOf("README.md", "README", "build.gradle.kts", "build.gradle", "package.json", "pom.xml", "Cargo.toml", "requirements.txt")
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
        return textResult(sb.toString())
    }
}
