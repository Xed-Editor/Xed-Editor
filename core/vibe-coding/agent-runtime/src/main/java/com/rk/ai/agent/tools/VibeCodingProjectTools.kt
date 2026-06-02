@file:OptIn(ExperimentalUuidApi::class)

package com.rk.ai.agent.tools

import com.google.gson.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService
import java.io.File

class VibeCodingProjectTools(private val ideService: IdeService) {

    private val getProjectStructure = Tool(
        name = "getProjectStructure",
        description = "Returns a hierarchical directory tree of the project.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Directory path to explore") }
                    putJsonObject("maxDepth") { put("type", "integer"); put("description", "Maximum depth (default: 3)") }
                    putJsonObject("maxItems") { put("type", "integer"); put("description", "Maximum items to return (default: 200)") }
                },
                required = emptyList<String>(),
            )
        },
        execute = { args ->
            val obj = args.asJsonObject
            val path = obj["path"]?.asJsonPrimitive?.asString ?: ideService.getPrimaryWorkspacePath()
            val maxDepth = obj["maxDepth"]?.asJsonPrimitive?.asInt ?: 3
            val maxItems = obj["maxItems"]?.asJsonPrimitive?.asInt ?: 200
            val structure = ideService.getProjectStructure(path, maxDepth, maxItems)
            listOf(UIMessagePart.Text(structure))
        },
    )

    private val getProjectSummary = Tool(
        name = "getProjectSummary",
        description = "Returns a high-level overview of the project including README, build files, config, open tabs, and git status.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Project path (default: workspace root)") }
                },
                required = emptyList<String>(),
            )
        },
        execute = { args ->
            val path = args.asJsonObject["path"]?.asJsonPrimitive?.asString
                ?: ideService.getPrimaryWorkspacePath()
            val config = ideService.getProjectConfig(path)
            val structure = ideService.getProjectStructure(path, 2, 100)
            val text = buildString {
                appendLine("Project: ${config["name"]?.asString ?: path.split("/").lastOrNull() ?: "Unknown"}")
                appendLine("Path: $path")
                config["configFiles"]?.asJsonArray?.let { files ->
                    if (files.size() > 0) {
                        appendLine("Config files: ${files.joinToString(", ") { it.asString }}")
                    }
                }
                appendLine()
                appendLine(structure)
            }
            listOf(UIMessagePart.Text(text))
        },
    )

    private val getProjectConfig = Tool(
        name = "getProjectConfig",
        description = "Detects project configuration including build system, language, and frameworks.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("workspacePath") { put("type", "string"); put("description", "Project path (default: workspace root)") }
                },
                required = emptyList<String>(),
            )
        },
        execute = { args ->
            val workspace = args.asJsonObject["workspacePath"]?.asJsonPrimitive?.asString ?: ideService.getPrimaryWorkspacePath()
            val config = ideService.getProjectConfig(workspace)
            val text = config.keySet().joinToString("\n") { "$it: ${config[it]}" }
            listOf(UIMessagePart.Text(text))
        },
    )

    private val getSymbolUnderCursor = Tool(
        name = "getSymbolUnderCursor",
        description = "Gets the symbol (function, class, variable) at the current cursor position in the active editor.",
        execute = { _ ->
            val symbol = ideService.getSymbolUnderCursor()
            if (symbol != null && symbol.keySet().size > 0) {
                val text = symbol.keySet().joinToString("\n") { "$it: ${symbol[it]}" }
                listOf(UIMessagePart.Text(text))
            } else {
                listOf(UIMessagePart.Text("No symbol at cursor"))
            }
        },
    )

    private val getProjectInstructions = Tool(
        name = "getProjectInstructions",
        description = "Reads project-level AI instruction files including CLAUDE.md, AGENTS.md (recursively from parent directories), .cursorrules, and copilot-instructions.md. These contain developer guidelines for AI behavior, coding conventions, and project-specific rules.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("workspacePath") { put("type", "string"); put("description", "Project path (default: workspace root)") }
                },
                required = emptyList<String>(),
            )
        },
        execute = { args ->
            val workspace = args.asJsonObject["workspacePath"]?.asJsonPrimitive?.asString ?: ideService.getPrimaryWorkspacePath()
            val workspaceFile = File(workspace)
            val sections = mutableListOf<String>()

            val rootCandidates = listOf(
                workspaceFile.resolve("CLAUDE.md"),
                workspaceFile.resolve(".claude/CLAUDE.md"),
                workspaceFile.resolve(".claude.md"),
                workspaceFile.resolve(".cursorrules"),
                workspaceFile.resolve(".github/copilot-instructions.md"),
            )
            for (candidate in rootCandidates) {
                if (candidate.exists() && candidate.isFile) {
                    sections.add("=== ${candidate.name} ===\n${candidate.readText()}")
                }
            }

            val agentsFiles = mutableListOf<File>()
            var dir = workspaceFile
            while (dir != null && dir.exists() && dir.isDirectory) {
                val agentsFile = dir.resolve("AGENTS.md")
                if (agentsFile.exists() && agentsFile.isFile) {
                    agentsFiles.add(agentsFile)
                }
                dir = dir.parentFile
            }
            for (file in agentsFiles.reversed()) {
                sections.add("=== AGENTS.md (${file.parentFile?.name ?: "/"}) ===\n${file.readText()}")
            }

            if (sections.isNotEmpty()) {
                listOf(UIMessagePart.Text("Project instructions found at ${workspaceFile.name}:\n\n${sections.joinToString("\n\n")}"))
            } else {
                listOf(UIMessagePart.Text("No project-level instructions file found (checked: CLAUDE.md, AGENTS.md recursively, .cursorrules, copilot-instructions.md)"))
            }
        },
    )

    private val searchProjectInstructions = Tool(
        name = "searchProjectInstructions",
        description = "Searches for AGENTS.md files at or near a specific subdirectory. AGENTS.md files contain per-directory developer guidelines for AI behavior. They can exist at any level of the project tree.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("path") { put("type", "string"); put("description", "Directory path to search from. Walks up to find the nearest AGENTS.md.") }
                },
                required = listOf("path"),
            )
        },
        execute = { args ->
            val searchPath = args.asJsonObject["path"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Missing path argument"))
            val searchFile = File(searchPath)
            val found = mutableListOf<File>()

            val directAgents = searchFile.resolve("AGENTS.md")
            if (directAgents.exists() && directAgents.isFile) found.add(directAgents)

            var dir = searchFile
            while (dir != null && dir.exists() && dir.isDirectory) {
                val agentsFile = dir.resolve("AGENTS.md")
                if (agentsFile.exists() && agentsFile.isFile) {
                    found.add(agentsFile)
                }
                dir = dir.parentFile
            }

            if (found.isNotEmpty()) {
                val sections = found.reversed().map { file ->
                    "=== ${file.parentFile?.name ?: "/"} (${file.absolutePath}) ===\n${file.readText()}"
                }
                listOf(UIMessagePart.Text("AGENTS.md files near $searchPath:\n\n${sections.joinToString("\n\n")}"))
            } else {
                listOf(UIMessagePart.Text("No AGENTS.md files found near: $searchPath"))
            }
        },
    )

    val all: List<Tool> = listOf(
        getProjectStructure, getProjectSummary, getProjectConfig,
        getSymbolUnderCursor, getProjectInstructions, searchProjectInstructions,
    )
}
