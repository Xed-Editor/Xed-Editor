package com.rk.ai.coding.tools

import com.google.gson.JsonObject
import com.rk.ai.models.UIMessagePart

class ToolExecutor(
    private val context: NativeToolContext,
    private val permissionManager: ToolPermissionManager,
) {
    suspend fun execute(
        tool: NativeTool,
        args: JsonObject,
        approved: Boolean = false,
    ): List<UIMessagePart> {
        return when (permissionManager.getPermission(tool.name)) {
            ToolPermissionLevel.Deny -> listOf(UIMessagePart.Text("""{"error":"Tool denied by policy: ${tool.name}"}""))
            ToolPermissionLevel.Ask -> {
                if (!approved) {
                    listOf(UIMessagePart.Text("""{"error":"Tool requires approval before execution: ${tool.name}"}""))
                } else {
                    tool.executeWithTimeout(args, context).toMessageParts()
                }
            }
            ToolPermissionLevel.AutoAllow -> tool.executeWithTimeout(args, context).toMessageParts()
        }
    }

    suspend fun preview(
        sessionId: String,
        toolCallId: String,
        toolName: String,
        input: JsonObject,
        rawInput: String,
    ): ToolApprovalRequest =
        permissionManager.buildPreview(sessionId, toolCallId, toolName, input, rawInput, context)
}
