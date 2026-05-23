package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class GetSymbolUnderCursorTool : BaseMcpTool() {
    override val name: String = "getSymbolUnderCursor"
    override val description: String = "Gets the symbol under the cursor in the active editor."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val result = context.ideService.getSymbolUnderCursor()
        return resultJson(result)
    }
}
