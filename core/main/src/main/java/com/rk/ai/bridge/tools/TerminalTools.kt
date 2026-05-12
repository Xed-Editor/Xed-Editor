package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.service.IdeService

class GetTerminalOutputTool : BaseMcpTool() {
    override fun getName(): String = "getTerminalOutput"
    override fun getDescription(): String = "Gets recent terminal transcript output."
    override fun getOptionalParams(): Map<String, String> = mapOf("lines" to "number")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val lines = optionalInt(args, "lines", -1).takeIf { it > 0 }
        val output = ideService.getTerminalOutput(lines)
        return textResult(output)
    }
}
