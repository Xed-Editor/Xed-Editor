package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService

class GetTerminalOutputTool : McpTool {
    override fun getName(): String = "getTerminalOutput"
    override fun getDescription(): String = "Gets recent terminal transcript output."
    override fun getOptionalParams(): Map<String, String> = mapOf("lines" to "number")
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val lines = args.get("lines")?.asInt
        val output = ideService.getTerminalOutput(lines)
        return textResult(output)
    }
}
