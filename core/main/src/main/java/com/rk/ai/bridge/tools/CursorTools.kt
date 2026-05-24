package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class GetSymbolUnderCursorTool : BaseMcpTool() {
    override fun getName(): String = "getSymbolUnderCursor"
    override fun getDescription(): String = "Gets the symbol under the cursor in the active editor."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val result = context.ideService.getSymbolUnderCursor()
        return McpToolResult.success(result.toString())
    }
}
