package com.rk.ai.agent.files

import android.content.Context
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

object AgentFileLoader : MarkdownFrontmatterLoader<FileAgentDefinition>(
    subDir = "agents",
    tag = "AgentFileLoader",
    builder = { id, frontmatter, body, file ->
        FileAgentDefinition(
            id = id,
            name = frontmatter["name"] ?: id.replaceFirstChar { it.uppercase() },
            description = frontmatter["description"] ?: "",
            model = frontmatter["model"],
            color = frontmatter["color"],
            allowedTools = frontmatter.getFrontmatterStringList("tools"),
            hidden = frontmatter.getFrontmatterBoolean("hidden"),
            prompt = body,
            sourceFile = file,
        )
    },
    fallback = { id, body, file ->
        FileAgentDefinition(
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
            description = "",
            prompt = body,
            sourceFile = file,
        )
    },
) {
    fun getAgentsDir(context: Context): File = getDir(context)
    fun listAgents(context: Context): List<FileAgentDefinition> = list(context)
}
