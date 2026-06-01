package com.rk.ai.agent.tools

import com.google.gson.JsonObject
import com.rk.ai.models.InputSchema
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.rk.ai.service.IdeService

class VibeCodingProjectTools(private val ideService: IdeService) {

    val all: List<Tool> = listOf(getProjectStructure, getProjectSummary, getProjectConfig, getSymbolUnderCursor)

    private val getProjectStructure = Tool(
        name = "getProjectStructure",
        description = "Returns a hierarchical directory tree of the project.",
        parameters = {
            InputSchema.Obj(
                properties = JsonObject().apply {
                    addProperty("path", "Directory path to explore")
                    add("maxDepth", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Maximum depth (default: 3)") })
                    add("maxItems", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Maximum items to return (default: 200)") })
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
                properties = JsonObject().apply {
                    addProperty("path", "Project path (default: workspace root)")
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
                properties = JsonObject().apply {
                    addProperty("workspacePath", "Project path (default: workspace root)")
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
            if (symbol != null && symbol.keySet().size() > 0) {
                val text = symbol.keySet().joinToString("\n") { "$it: ${symbol[it]}" }
                listOf(UIMessagePart.Text(text))
            } else {
                listOf(UIMessagePart.Text("No symbol at cursor"))
            }
        },
    )
}
