package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class ContextManagerTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Context"
    override fun getName(): String = "manageContext"
    override fun getDescription(): String = """Manages AI context window. Compress, summarize, or extract relevant context 
from codebase. Helps stay within token limits while maintaining understanding."""

    override fun getRequiredParams(): Map<String, String> = mapOf("action" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "target" to "string",
        "maxTokens" to "number",
        "preserve" to "string",
        "summarize" to "boolean"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "action" to "Action: 'summarize', 'compress', 'extract', 'prioritize', 'split'"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "target" to "File path, 'project', 'selection', or 'openFiles'",
        "maxTokens" to "Maximum tokens in output (default: 8000)",
        "preserve" to "What to preserve: 'functions', 'classes', 'imports', 'all' (default: all)",
        "summarize" to "Generate summary instead of full content (default: false)"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val action = requireString(args, "action")
        val target = optionalString(args, "target", "openFiles")
        val maxTokens = optionalInt(args, "maxTokens") ?: 8000
        val preserve = optionalString(args, "preserve", "all")
        val summarize = optionalBoolean(args, "summarize", false)

        val content = when (target.lowercase()) {
            "project" -> {
                val workspacePath = context.ideService.getPrimaryWorkspacePath()
                context.ideService.getProjectStructure(workspacePath, 5, 200)
            }
            "selection" -> {
                val selection = context.ideService.getSelection()
                if (selection.isBlank()) return McpToolResult.error("No selection")
                selection
            }
            "openfiles" -> {
                val files = context.ideService.getOpenFiles()
                if (files.isEmpty()) return McpToolResult.error("No open files")
                files.joinToString("\n") { it.toString() }
            }
            else -> {
                val file = resolvePathOrThrow(context, target)
                context.ideService.getFileContent(file.absolutePath)
                    ?: return McpToolResult.error("Could not read file: $target")
            }
        }

        if (content.isBlank()) return McpToolResult.error("No content to process")

        val result = when (action.lowercase()) {
            "summarize" -> summarizeContent(content, target, maxTokens)
            "compress" -> compressContent(content, target, maxTokens, preserve)
            "extract" -> extractKeyInfo(content, target, preserve)
            "prioritize" -> prioritizeContent(content, target, maxTokens)
            "split" -> splitContext(content, target, maxTokens)
            else -> return McpToolResult.error("Unknown action: $action. Use: summarize, compress, extract, prioritize, split")
        }

        return McpToolResult.success(result, mapOf(
            "action" to action,
            "target" to target,
            "originalLength" to content.length,
            "processedLength" to result.length,
            "maxTokens" to maxTokens
        ))
    }

    private fun summarizeContent(content: String, target: String, maxTokens: Int): String {
        return buildString {
            appendLine("## Context Summary Request")
            appendLine("**Target:** $target")
            appendLine("**Max Tokens:** $maxTokens")
            appendLine()
            appendLine("### Instructions for AI Agent:")
            appendLine("Summarize the following content concisely, preserving:")
            appendLine("- Key function/class names and their purposes")
            appendLine("- Important constants and configurations")
            appendLine("- Public API surface")
            appendLine("- Critical business logic")
            appendLine()
            appendLine("### Content to Summarize:")
            appendLine("```")
            appendLine(content.take(100000))
            appendLine("```")
        }
    }

    private fun compressContent(content: String, target: String, maxTokens: Int, preserve: String): String {
        return buildString {
            appendLine("## Context Compression Request")
            appendLine("**Target:** $target")
            appendLine("**Max Tokens:** $maxTokens")
            appendLine("**Preserve:** $preserve")
            appendLine()
            appendLine("### Instructions for AI Agent:")
            appendLine("Compress the following content to fit within $maxTokens tokens while preserving:")
            when (preserve) {
                "functions" -> appendLine("- All function signatures and their docstrings")
                "classes" -> appendLine("- All class definitions and their public methods")
                "imports" -> appendLine("- All imports and essential code structure")
                "all" -> appendLine("- Essential code structure and logic")
            }
            appendLine("- Remove comments, blank lines, and non-essential whitespace")
            appendLine("- Abbreviate variable names where safe")
            appendLine()
            appendLine("### Content to Compress:")
            appendLine("```")
            appendLine(content.take(100000))
            appendLine("```")
        }
    }

    private fun extractKeyInfo(content: String, target: String, preserve: String): String {
        return buildString {
            appendLine("## Key Information Extraction")
            appendLine("**Target:** $target")
            appendLine("**Extract:** $preserve")
            appendLine()
            appendLine("### Instructions for AI Agent:")
            appendLine("Extract the following key information from the code:")
            when (preserve) {
                "functions" -> {
                    appendLine("- Function names, parameters, return types")
                    appendLine("- Function purposes (from docstrings or inference)")
                }
                "classes" -> {
                    appendLine("- Class names, inheritance hierarchy")
                    appendLine("- Public methods and properties")
                }
                "imports" -> {
                    appendLine("- All import statements")
                    appendLine("- External dependencies used")
                }
                "all" -> {
                    appendLine("- File structure and organization")
                    appendLine("- Key functions and classes")
                    appendLine("- Dependencies and imports")
                    appendLine("- Constants and configurations")
                }
            }
            appendLine()
            appendLine("### Source Code:")
            appendLine("```")
            appendLine(content.take(100000))
            appendLine("```")
        }
    }

    private fun prioritizeContent(content: String, target: String, maxTokens: Int): String {
        return buildString {
            appendLine("## Content Prioritization")
            appendLine("**Target:** $target")
            appendLine("**Max Tokens:** $maxTokens")
            appendLine()
            appendLine("### Instructions for AI Agent:")
            appendLine("Prioritize the content by importance, keeping the most critical parts:")
            appendLine("1. Core business logic and algorithms")
            appendLine("2. Public API surface")
            appendLine("3. Error handling and edge cases")
            appendLine("4. Configuration and constants")
            appendLine("5. Helper functions (summarize less important ones)")
            appendLine()
            appendLine("### Content to Prioritize:")
            appendLine("```")
            appendLine(content.take(100000))
            appendLine("```")
        }
    }

    private fun splitContext(content: String, target: String, maxTokens: Int): String {
        val lines = content.lines()
        val chunkSize = maxTokens / 4 // Approximate lines per chunk
        val chunks = lines.chunked(chunkSize)

        return buildString {
            appendLine("## Context Split")
            appendLine("**Target:** $target")
            appendLine("**Total Lines:** ${lines.size}")
            appendLine("**Chunks:** ${chunks.size}")
            appendLine()
            chunks.forEachIndexed { index, chunk ->
                appendLine("### Chunk ${index + 1}/${chunks.size}")
                appendLine("```")
                appendLine(chunk.joinToString("\n"))
                appendLine("```")
                appendLine()
            }
        }
    }
}
