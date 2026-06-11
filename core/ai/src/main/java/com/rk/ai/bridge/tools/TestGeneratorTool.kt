package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class TestGeneratorTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Code Generation"
    override fun getName(): String = "generateTests"
    override fun getDescription(): String = """Generates unit tests for the specified code."""

    override fun getRequiredParams(): Map<String, String> = mapOf("target" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "framework" to "string",
        "style" to "string",
        "coverage" to "string",
        "includeMocks" to "boolean",
        "language" to "string"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "target" to "What to test: file path, 'selection', 'activeFile', or function/class name"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "framework" to "Test framework (default: auto-detect)",
        "style" to "Test style: 'unit', 'integration', 'edge-cases', 'comprehensive' (default: unit)",
        "coverage" to "Coverage focus: 'full', 'critical', 'boundary' (default: full)",
        "includeMocks" to "Include mock objects (default: true)",
        "language" to "Override language detection"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val target = requireString(args, "target")
        val framework = optionalString(args, "framework", "auto")
        val style = optionalString(args, "style", "unit")
        val coverage = optionalString(args, "coverage", "full")
        val includeMocks = optionalBoolean(args, "includeMocks", true)
        val language = optionalString(args, "language")

        val (code, filePath) = when (target.lowercase()) {
            "selection" -> {
                val selection = context.ideService.getSelection()
                if (selection.isBlank()) return McpToolResult.error("No selection")
                val active = context.ideService.getActiveFile()
                val activePath = active?.get("filePath")?.asString ?: "selection"
                selection to activePath
            }
            "activefile" -> {
                val active = context.ideService.getActiveFile()
                    ?: return McpToolResult.error("No active file")
                val activePath = active.get("filePath")?.asString
                    ?: return McpToolResult.error("Could not determine active file path")
                val content = context.ideService.getFileContent(activePath)
                    ?: return McpToolResult.error("Could not read active file")
                content to activePath
            }
            else -> {
                val file = resolvePathOrThrow(context, target)
                val content = context.ideService.getFileContent(file.absolutePath)
                    ?: return McpToolResult.error("Could not read file: $target")
                content to file.absolutePath
            }
        }

        if (code.isBlank()) return McpToolResult.error("No code to generate tests for")

        val detectedLang = language ?: detectLanguage(filePath)
        val detectedFramework = if (framework == "auto") detectFramework(detectedLang) else framework
        val testPrompt = buildTestPrompt(code, detectedLang, detectedFramework, style, coverage, includeMocks)

        return McpToolResult.success(
            buildString {
                appendLine("## Test Generation Request")
                appendLine("**Source:** $filePath")
                appendLine("**Language:** $detectedLang")
                appendLine("**Framework:** $detectedFramework")
                appendLine("**Style:** $style")
                appendLine("**Coverage:** $coverage")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine(testPrompt)
                appendLine()
                appendLine("### Source Code:")
                appendLine("```$detectedLang")
                appendLine(code.take(50000))
                if (code.length > 50000) appendLine("\n... (truncated)")
                appendLine("```")
            },
            emptyMap()
        )
    }

    private fun buildTestPrompt(code: String, language: String, framework: String, style: String, coverage: String, includeMocks: Boolean): String = buildString {
        appendLine("You are an expert test engineer. Generate comprehensive $framework tests for the following $language code.")
        appendLine()
        appendLine("Test generation rules:")
        appendLine("- Follow $framework conventions and best practices")
        appendLine("- Use descriptive test names that explain the expected behavior")
        when (style) {
            "unit" -> appendLine("- Focus on unit tests for individual functions/methods")
            "integration" -> appendLine("- Focus on integration tests with real dependencies")
            "edge-cases" -> appendLine("- Focus on edge cases, boundary conditions, and error scenarios")
            "comprehensive" -> appendLine("- Generate a mix of unit, integration, and edge case tests")
        }
        when (coverage) {
            "full" -> appendLine("- Cover all public methods and important private ones")
            "critical" -> appendLine("- Focus on critical business logic and security-sensitive code")
            "boundary" -> appendLine("- Focus on boundary conditions and error handling")
        }
        if (includeMocks) {
            appendLine("- Include mock objects for external dependencies")
        }
        appendLine()
        appendLine("Respond with test file name, framework, and array of test cases.")
    }

    private fun detectFramework(language: String): String = when (language) {
        "kotlin" -> "junit + mockk"
        "java" -> "junit + mockito"
        "python" -> "pytest"
        "javascript", "typescript" -> "vitest"
        "go" -> "go test"
        "rust" -> "cargo test"
        "ruby" -> "rspec"
        "csharp" -> "nunit"
        else -> "junit"
    }

    private fun detectLanguage(path: String): String {
        val ext = path.substringAfterLast(".").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            "rs" -> "rust"
            "go" -> "go"
            "rb" -> "ruby"
            "cs" -> "csharp"
            else -> "text"
        }
    }
}
