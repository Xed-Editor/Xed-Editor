package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class GetTerminalOutputTool : BaseMcpTool() {
    override val name: String = "getTerminalOutput"
    override val description: String = "Gets recent terminal transcript output."
    override val optionalParams: Map<String, String> = mapOf("lines" to "number")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val lines = optionalInt(args, "lines", -1).takeIf { it > 0 }
        val output = context.ideService.getTerminalOutput(lines)
        return resultText(enforceOutputLimit(output))
    }
}
