package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.GeminiIdeService

class GetTerminalOutputTool : McpTool {
    override fun getName(): String = "getTerminalOutput"
    override suspend fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val lines = args.get("lines")?.asInt
        val output = ideService.getTerminalOutput(lines)
        return textResult(output)
    }
}
