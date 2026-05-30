package com.rk.ai.coding.tools

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

class ToolRegistry(
    private val permissionManager: ToolPermissionManager = ToolPermissionManager(),
    private val mcpToolSource: McpToolSource? = null,
) {
    private val tools = linkedMapOf<String, NativeTool>()

    init {
        registerInitialTools()
    }

    fun register(tool: NativeTool) {
        tools[tool.name] = tool
    }

    fun get(name: String): NativeTool? = tools[name]

    fun listNames(): Set<String> = tools.keys

    fun asGenerationTools(context: NativeToolContext): List<Tool> {
        val executor = ToolExecutor(context, permissionManager)
        val nativeTools = tools.values.map { tool -> tool.toGenerationTool(executor) }
        return nativeTools + externalMcpTools()
    }

    suspend fun execute(name: String, args: JsonObject, context: NativeToolContext, approved: Boolean = false): List<UIMessagePart> {
        val tool = get(name) ?: return listOf(UIMessagePart.Text("""{"error":"Unknown tool: $name"}""))
        return ToolExecutor(context, permissionManager).execute(tool, args, approved)
    }

    fun permissionManager(): ToolPermissionManager = permissionManager

    private fun registerInitialTools() {
        register(ReadFileTool())
        register(WriteFileTool())
        register(SearchTool())
        register(ListFilesTool())
        register(GitStatusTool())
        register(GitDiffTool())
        register(TerminalReadTool())
    }

    private fun NativeTool.toGenerationTool(executor: ToolExecutor): Tool {
        val nativeTool = this
        return Tool(
            name = nativeTool.name,
            description = nativeTool.description,
            parameters = { nativeTool.inputSchema() },
            needsApproval = permissionManager.needsApproval(nativeTool.name),
            execute = { args ->
                val gsonArgs = args.toGsonObject()
                val approved = permissionManager.getPermission(nativeTool.name) == ToolPermissionLevel.Ask
                executor.execute(nativeTool, gsonArgs, approved = approved)
            },
        )
    }

    private fun externalMcpTools(): List<Tool> {
        val source = mcpToolSource ?: return emptyList()
        return source.availableTools().map { tool ->
            Tool(
                name = tool.name,
                description = tool.description,
                parameters = { tool.inputSchema },
                needsApproval = tool.needsApproval || permissionManager.needsApproval(tool.name),
                execute = { args -> source.callTool(tool.serverId, tool.name, args.jsonObject) },
            )
        }
    }

    private fun JsonElement.toGsonObject(): JsonObject {
        val element = JsonParser.parseString(toString())
        return if (element.isJsonObject) element.asJsonObject else JsonObject()
    }
}
