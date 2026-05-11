package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.GeminiIdeService
import kotlinx.coroutines.runBlocking

class RunCommandTool : McpTool {
    override fun getName(): String = "runCommand"
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val command = args.get("command")?.asString.orEmpty()
        if (command.isBlank()) throw IllegalArgumentException("command required")
        val timeout = args.get("timeoutSeconds")?.asLong ?: 120L
        
        val result = runBlocking { ideService.runCommand(command, timeout) }
        
        val text = buildString {
            if (result.output.isNotBlank()) appendLine(result.output)
            if (result.error.isNotBlank()) appendLine(result.error)
            append("exit ${result.exitCode}")
            if (result.timedOut) append(" (timed out)")
        }
        return textResult(text)
    }
}

class ShowMessageTool : McpTool {
    override fun getName(): String = "showMessage"
    override fun execute(args: JsonObject, ideService: GeminiIdeService): JsonObject {
        val message = args.get("message")?.asString.orEmpty()
        ideService.showMessage(message)
        return textResult("shown")
    }
}
