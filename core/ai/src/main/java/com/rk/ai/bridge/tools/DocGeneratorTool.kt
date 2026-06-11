package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class DocGeneratorTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Code Generation"
    override fun getName(): String = "generateDocs"
    override fun getDescription(): String = """Generates documentation for code. Supports multiple formats:
README, API docs, inline comments, and more."""

    override fun getRequiredParams(): Map<String, String> = mapOf("target" to "string", "format" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "style" to "string",
        "audience" to "string",
        "includeExamples" to "boolean",
        "language" to "string"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "target" to "What to document: file path, 'selection', 'project', or 'openFiles'",
        "format" to "Output format: 'readme', 'api', 'inline', 'changelog', 'adr'"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "style" to "Documentation style: 'concise', 'detailed', 'tutorial' (default: detailed)",
        "audience" to "Target audience: 'developer', 'user', 'api' (default: developer)",
        "includeExamples" to "Include code examples (default: true)",
        "language" to "Override language detection"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val target = requireString(args, "target")
        val format = requireString(args, "format")
        val style = optionalString(args, "style", "detailed")
        val audience = optionalString(args, "audience", "developer")
        val includeExamples = optionalBoolean(args, "includeExamples", true)
        val language = optionalString(args, "language")

        val (code, filePath) = when (target.lowercase()) {
            "selection" -> {
                val selection = context.ideService.getSelection()
                if (selection.isBlank()) return McpToolResult.error("No selection")
                val active = context.ideService.getActiveFile()
                val activePath = active?.get("filePath")?.asString ?: "selection"
                selection to activePath
            }
            "project" -> {
                "" to context.ideService.getPrimaryWorkspacePath()
            }
            "openfiles" -> {
                val files = context.ideService.getOpenFiles()
                if (files.isEmpty()) return McpToolResult.error("No open files")
                files.joinToString("\n") { it.toString() } to "open_files"
            }
            else -> {
                val file = resolvePathOrThrow(context, target)
                val content = context.ideService.getFileContent(file.absolutePath)
                    ?: return McpToolResult.error("Could not read file: $target")
                content to file.absolutePath
            }
        }

        val detectedLang = language ?: detectLanguage(filePath)
        val docPrompt = buildDocPrompt(code, detectedLang, format, style, audience, includeExamples)

        return McpToolResult.success(
            buildString {
                appendLine("## Documentation Generation Request")
                appendLine("**Target:** $target")
                appendLine("**Format:** $format")
                appendLine("**Language:** $detectedLang")
                appendLine("**Style:** $style")
                appendLine("**Audience:** $audience")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine(docPrompt)
                if (code.isNotBlank() && target.lowercase() != "project") {
                    appendLine()
                    appendLine("### Source Code:")
                    appendLine("```$detectedLang")
                    appendLine(code.take(50000))
                    if (code.length > 50000) appendLine("\n... (truncated)")
                    appendLine("```")
                }
            },
            emptyMap()
        )
    }

    private fun buildDocPrompt(code: String, language: String, format: String, style: String, audience: String, includeExamples: Boolean): String = buildString {
        appendLine("You are a technical writer specializing in software documentation.")
        appendLine()
        when (format.lowercase()) {
            "readme" -> {
                appendLine("Generate a comprehensive README.md for this code/project.")
                appendLine("Include: project overview, installation, usage examples, API reference, configuration, contributing guidelines.")
            }
            "api" -> {
                appendLine("Generate API documentation for this code.")
                appendLine("Include: endpoint/function signatures, parameter descriptions, return values, usage examples, error handling.")
            }
            "inline" -> {
                appendLine("Generate inline documentation for this code.")
                appendLine("Add: function/method docstrings, class documentation, complex logic comments, type annotations.")
            }
            "changelog" -> {
                appendLine("Generate a changelog entry following Keep a Changelog format.")
            }
            "adr" -> {
                appendLine("Generate an Architecture Decision Record (ADR). Include: title, status, context, decision, consequences.")
            }
        }
    }

    private fun detectLanguage(path: String): String {
        val ext = path.substringAfterLast(".").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            else -> "text"
        }
    }
}
