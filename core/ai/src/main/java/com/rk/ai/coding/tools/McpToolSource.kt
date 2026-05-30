package com.rk.ai.coding.tools

import com.rk.ai.mcp.McpManager
import com.rk.ai.models.InputSchema
import com.rk.ai.models.UIMessagePart
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ExternalMcpTool(
    val serverId: Uuid,
    val name: String,
    val description: String,
    val inputSchema: InputSchema?,
    val needsApproval: Boolean,
)

@OptIn(ExperimentalUuidApi::class)
interface McpToolSource {
    fun availableTools(): List<ExternalMcpTool>
    suspend fun callTool(serverId: Uuid, toolName: String, args: JsonObject): List<UIMessagePart>
}

@OptIn(ExperimentalUuidApi::class)
class McpManagerToolSource(
    private val mcpManager: McpManager,
) : McpToolSource {
    override fun availableTools(): List<ExternalMcpTool> =
        mcpManager.getAllAvailableTools().map { (serverId, tool) ->
            ExternalMcpTool(
                serverId = serverId,
                name = tool.name,
                description = tool.description.orEmpty(),
                inputSchema = tool.inputSchema,
                needsApproval = tool.needsApproval,
            )
        }

    override suspend fun callTool(serverId: Uuid, toolName: String, args: JsonObject): List<UIMessagePart> =
        mcpManager.callTool(serverId, toolName, args)
}
