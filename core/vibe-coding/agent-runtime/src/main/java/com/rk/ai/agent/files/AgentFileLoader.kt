package com.rk.ai.agent.files

import android.content.Context
import android.util.Log
import java.io.File

data class FileAgentDefinition(
    val id: String,
    val name: String,
    val description: String,
    val model: String? = null,
    val color: String? = null,
    val allowedTools: List<String> = emptyList(),
    val hidden: Boolean = false,
    val prompt: String,
    val sourceFile: File? = null,
)

object AgentFileLoader {
    private const val TAG = "AgentFileLoader"
    private const val AGENTS_DIR = "agents"

    fun getAgentsDir(context: Context): File {
        val dir = context.filesDir.resolve(AGENTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listAgents(context: Context): List<FileAgentDefinition> {
        val dir = getAgentsDir(context)
        return dir.listFiles()
            ?.filter { it.extension == "md" }
            ?.mapNotNull { file -> parseAgentFile(file) }
            ?: emptyList()
    }

    fun getAgent(context: Context, name: String): FileAgentDefinition? {
        val file = getAgentsDir(context).resolve("$name.md")
        if (!file.exists()) return null
        return parseAgentFile(file)
    }

    fun saveAgent(context: Context, name: String, content: String): FileAgentDefinition? {
        val dir = getAgentsDir(context)
        dir.mkdirs()
        val file = dir.resolve("$name.md")
        file.writeText(content)
        return parseAgentFile(file)
    }

    fun deleteAgent(context: Context, name: String): Boolean {
        val file = getAgentsDir(context).resolve("$name.md")
        return file.delete()
    }

    private fun parseAgentFile(file: File): FileAgentDefinition? {
        return try {
            val content = file.readText()
            val name = file.nameWithoutExtension

            if (!content.startsWith("---")) {
                return FileAgentDefinition(
                    id = name,
                    name = name.replaceFirstChar { it.uppercase() },
                    description = "",
                    prompt = content.trim(),
                    sourceFile = file,
                )
            }

            val endMarker = content.indexOf("\n---", startIndex = 3)
            if (endMarker == -1) return null

            val frontmatter = content.substring(3, endMarker).trim()
            val body = content.substring(endMarker + 4).trim()

            val fields = parseFrontmatter(frontmatter)
            val agentName = fields["name"] ?: name.replaceFirstChar { it.uppercase() }
            val description = fields["description"] ?: ""
            val model = fields["model"]
            val color = fields["color"]
            val hidden = fields["hidden"]?.toBoolean() ?: false
            val toolsStr = fields["tools"]
            val allowedTools = if (toolsStr != null) {
                toolsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else emptyList()

            FileAgentDefinition(
                id = name,
                name = agentName,
                description = description,
                model = model,
                color = color,
                allowedTools = allowedTools,
                hidden = hidden,
                prompt = body,
                sourceFile = file,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse agent file: ${file.name}", e)
            null
        }
    }

    private fun parseFrontmatter(yaml: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        yaml.lines().forEach { line ->
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim()
                val value = line.substring(colonIdx + 1).trim().removeSurrounding("\"")
                if (key.isNotBlank()) {
                    result[key] = value
                }
            }
        }
        return result
    }
}
