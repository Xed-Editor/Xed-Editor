package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class GetTerminalOutputTool : BaseMcpTool() {
    override fun getCategory(): String = "Terminal"
    override fun getName(): String = "getTerminalOutput"
    override fun getDescription(): String = "Gets recent terminal transcript output."
    override fun getOptionalParams(): Map<String, String> = mapOf("lines" to "number")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "lines" to "Number of recent lines to retrieve (default: all available)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val lines = optionalPositiveInt(args, "lines")
        val output = context.ideService.getTerminalOutput(lines)
        return McpToolResult.success(output)
    }
}
