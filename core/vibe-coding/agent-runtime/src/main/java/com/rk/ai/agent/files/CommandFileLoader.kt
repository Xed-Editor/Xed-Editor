package com.rk.ai.agent.files

import android.content.Context
import java.io.File

data class CommandDefinition(
    val id: String,
    val name: String,
    val description: String,
    val model: String? = null,
    val subtask: Boolean = false,
    val hidden: Boolean = false,
    val argumentHint: String? = null,
    val prompt: String,
    val category: String = "General",
    val sourceFile: File? = null,
)

private fun guessCategory(content: String): String {
    val lower = content.lowercase()
    return when {
        "git" in lower || "commit" in lower || "push" in lower || "branch" in lower -> "Git"
        "test" in lower || "build" in lower || "compile" in lower -> "Code"
        "feature" in lower || "implement" in lower || "design" in lower || "architect" in lower -> "Feature"
        "translate" in lower || "doc" in lower || "changelog" in lower -> "Project"
        "review" in lower || "audit" in lower || "security" in lower -> "Code"
        else -> "General"
    }
}

object CommandFileLoader : MarkdownFrontmatterLoader<CommandDefinition>(
    subDir = "commands",
    tag = "CommandFileLoader",
    builder = { id, frontmatter, body, file ->
        CommandDefinition(
            id = id,
            name = frontmatter["name"] ?: id.replaceFirstChar { it.uppercase() },
            description = frontmatter["description"] ?: "",
            model = frontmatter["model"],
            subtask = frontmatter["subtask"]?.toBooleanStrictOrNull() ?: false,
            hidden = frontmatter["hidden"]?.toBooleanStrictOrNull() ?: false,
            argumentHint = frontmatter["argument-hint"],
            prompt = body,
            category = frontmatter["category"] ?: guessCategory(body),
            sourceFile = file,
        )
    },
    fallback = { id, body, file ->
        CommandDefinition(
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
            description = "",
            prompt = body,
            category = guessCategory(body),
            sourceFile = file,
        )
    },
) {
    fun getCommandsDir(context: Context): File = getDir(context)
    fun listCommands(context: Context): List<CommandDefinition> = list(context)
}
