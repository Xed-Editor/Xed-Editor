package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import java.io.File

class CustomInstructionsTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Configuration"
    override fun getName(): String = "customInstructions"
    override fun getDescription(): String = """Manages custom AI instructions per project. Stores instructions in .xed/instructions.md.
These instructions are automatically loaded by AI agents when working on the project."""

    override fun getRequiredParams(): Map<String, String> = mapOf("action" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "instructions" to "string",
        "category" to "string"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "action" to "Action: 'get', 'set', 'append', 'list', 'delete'"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "instructions" to "Instructions text (required for set/append)",
        "category" to "Instruction category: 'general', 'style', 'architecture', 'testing', 'security'"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val action = requireString(args, "action")
        val instructions = optionalString(args, "instructions")
        val category = optionalString(args, "category", "general")

        val workspacePath = context.ideService.getPrimaryWorkspacePath()
        val instructionsDir = File(workspacePath, ".xed")
        val instructionsFile = File(instructionsDir, "instructions.md")

        return when (action.lowercase()) {
            "get" -> getInstructions(instructionsFile)
            "set" -> {
                if (instructions.isNullOrBlank()) return McpToolResult.error("Instructions text is required for 'set' action")
                setInstructions(instructionsFile, instructions, category)
            }
            "append" -> {
                if (instructions.isNullOrBlank()) return McpToolResult.error("Instructions text is required for 'append' action")
                appendInstructions(instructionsFile, instructions, category)
            }
            "list" -> listInstructions(instructionsDir)
            "delete" -> deleteInstructions(instructionsFile, category)
            else -> McpToolResult.error("Unknown action: $action. Use: get, set, append, list, delete")
        }
    }

    private fun getInstructions(file: File): McpToolResult {
        if (!file.exists()) {
            return McpToolResult.success(
                buildString {
                    appendLine("## Custom Instructions")
                    appendLine()
                    appendLine("No custom instructions set for this project.")
                    appendLine()
                    appendLine("### How to Add Instructions:")
                    appendLine("Use the `set` action to create instructions:")
                    appendLine("```json")
                    appendLine("""{
  "action": "set",
  "instructions": "Your instructions here...",
  "category": "general"
}""")
                    appendLine("```")
                    appendLine()
                    appendLine("### Available Categories:")
                    appendLine("- `general` - General project instructions")
                    appendLine("- `style` - Code style guidelines")
                    appendLine("- `architecture` - Architecture decisions")
                    appendLine("- `testing` - Testing requirements")
                    appendLine("- `security` - Security guidelines")
                }
            )
        }

        val content = file.readText()
        return McpToolResult.success(
            buildString {
                appendLine("## Custom Instructions")
                appendLine()
                appendLine(content)
            },
            mapOf("file" to file.absolutePath, "length" to content.length)
        )
    }

    private fun setInstructions(file: File, instructions: String, category: String): McpToolResult {
        file.parentFile?.mkdirs()

        val header = if (file.exists()) {
            // Replace existing category section
            val existing = file.readText()
            val categoryHeader = "## $category"
            val categoryIndex = existing.indexOf(categoryHeader)

            if (categoryIndex >= 0) {
                val nextCategoryIndex = existing.indexOf("\n## ", categoryIndex + categoryHeader.length)
                val endIndex = if (nextCategoryIndex >= 0) nextCategoryIndex else existing.length
                existing.substring(0, categoryIndex) + "$categoryHeader\n\n$instructions\n" + existing.substring(endIndex)
            } else {
                existing + "\n\n## $category\n\n$instructions\n"
            }
        } else {
            "# Project Instructions\n\n## $category\n\n$instructions\n"
        }

        file.writeText(header)

        return McpToolResult.success(
            "Instructions saved to ${file.absolutePath}",
            mapOf("file" to file.absolutePath, "category" to category)
        )
    }

    private fun appendInstructions(file: File, instructions: String, category: String): McpToolResult {
        file.parentFile?.mkdirs()

        val content = if (file.exists()) {
            val existing = file.readText()
            val categoryHeader = "## $category"
            val categoryIndex = existing.indexOf(categoryHeader)

            if (categoryIndex >= 0) {
                val nextCategoryIndex = existing.indexOf("\n## ", categoryIndex + categoryHeader.length)
                val endIndex = if (nextCategoryIndex >= 0) nextCategoryIndex else existing.length
                val beforeCategory = existing.substring(0, endIndex)
                val afterCategory = existing.substring(endIndex)
                beforeCategory + "\n- " + instructions.replace("\n", "\n- ") + afterCategory
            } else {
                existing + "\n\n## $category\n\n- " + instructions.replace("\n", "\n- ") + "\n"
            }
        } else {
            "# Project Instructions\n\n## $category\n\n- " + instructions.replace("\n", "\n- ") + "\n"
        }

        file.writeText(content)

        return McpToolResult.success(
            "Instructions appended to ${file.absolutePath}",
            mapOf("file" to file.absolutePath, "category" to category)
        )
    }

    private fun listInstructions(dir: File): McpToolResult {
        if (!dir.exists()) {
            return McpToolResult.success("No .xed directory found. No instructions set.")
        }

        val files = dir.listFiles { f -> f.name.endsWith(".md") || f.name.endsWith(".txt") } ?: emptyArray()

        return McpToolResult.success(
            buildString {
                appendLine("## Instruction Files")
                appendLine()
                if (files.isEmpty()) {
                    appendLine("No instruction files found.")
                } else {
                    files.forEach { file ->
                        appendLine("- **${file.name}** (${file.length()} bytes)")
                    }
                }
            },
            mapOf("count" to files.size)
        )
    }

    private fun deleteInstructions(file: File, category: String): McpToolResult {
        if (!file.exists()) {
            return McpToolResult.success("No instructions file found.")
        }

        if (category == "all") {
            file.delete()
            return McpToolResult.success("All instructions deleted.")
        }

        val existing = file.readText()
        val categoryHeader = "## $category"
        val categoryIndex = existing.indexOf(categoryHeader)

        if (categoryIndex < 0) {
            return McpToolResult.success("No instructions found for category: $category")
        }

        val nextCategoryIndex = existing.indexOf("\n## ", categoryIndex + categoryHeader.length)
        val endIndex = if (nextCategoryIndex >= 0) nextCategoryIndex else existing.length
        val newContent = existing.substring(0, categoryIndex) + existing.substring(endIndex)

        if (newContent.isBlank() || newContent.trim() == "# Project Instructions") {
            file.delete()
        } else {
            file.writeText(newContent)
        }

        return McpToolResult.success("Instructions for category '$category' deleted.")
    }
}
