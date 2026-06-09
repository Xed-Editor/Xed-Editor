package com.rk.ai.agent.files

import android.content.Context
import android.util.Log
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

object CommandFileLoader {
    private const val TAG = "CommandFileLoader"
    private const val COMMANDS_DIR = "commands"

    fun getCommandsDir(context: Context): File {
        val dir = context.filesDir.resolve(COMMANDS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listCommands(context: Context): List<CommandDefinition> {
        val dir = getCommandsDir(context)
        return dir.listFiles()
            ?.filter { it.extension == "md" }
            ?.mapNotNull { file -> parseCommandFile(file) }
            ?: emptyList()
    }

    fun getCommand(context: Context, name: String): CommandDefinition? {
        val file = getCommandsDir(context).resolve("$name.md")
        if (!file.exists()) return null
        return parseCommandFile(file)
    }

    fun saveCommand(context: Context, name: String, content: String): CommandDefinition? {
        val dir = getCommandsDir(context)
        dir.mkdirs()
        val file = dir.resolve("$name.md")
        file.writeText(content)
        return parseCommandFile(file)
    }

    fun deleteCommand(context: Context, name: String): Boolean {
        val file = getCommandsDir(context).resolve("$name.md")
        return file.delete()
    }

    private fun parseCommandFile(file: File): CommandDefinition? {
        return try {
            val content = file.readText()
            val name = file.nameWithoutExtension

            if (!content.startsWith("---")) {
                return CommandDefinition(
                    id = name,
                    name = name.replaceFirstChar { it.uppercase() },
                    description = "",
                    prompt = content.trim(),
                    category = guessCategory(content),
                    sourceFile = file,
                )
            }

            val endMarker = content.indexOf("\n---", startIndex = 3)
            if (endMarker == -1) return null

            val frontmatter = content.substring(3, endMarker).trim()
            val body = content.substring(endMarker + 4).trim()

            val fields = parseFrontmatter(frontmatter)
            val cmdName = fields["name"] ?: name.replaceFirstChar { it.uppercase() }
            val description = fields["description"] ?: ""
            val model = fields["model"]
            val subtask = fields["subtask"]?.toBoolean() ?: false
            val hidden = fields["hidden"]?.toBoolean() ?: false
            val argHint = fields["argument-hint"]
            val category = fields["category"] ?: guessCategory(body)

            CommandDefinition(
                id = name,
                name = cmdName,
                description = description,
                model = model,
                subtask = subtask,
                hidden = hidden,
                argumentHint = argHint,
                prompt = body,
                category = category,
                sourceFile = file,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse command file: ${file.name}", e)
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
}
