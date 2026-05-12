package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.IdeBridge
import com.rk.ai.bridge.McpTool
import com.rk.ai.service.IdeService

class GetIdeInfoTool : McpTool {
    override fun getName(): String = "getIdeInfo"
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val info = IdeBridge.getBridgeInfo()
        return JsonObject().apply {
            add("content", com.google.gson.JsonArray().apply {
                add(com.google.gson.JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", JsonObject().apply {
                        addProperty("name", "Xed Editor")
                        addProperty("version", com.rk.xededitor.BuildConfig.VERSION_NAME)
                        addProperty("bridgePort", info?.port ?: -1)
                        addProperty("bridgeRunning", IdeBridge.isRunning())
                        addProperty("clients", IdeBridge.connectedClients())
                        addProperty("workspace", ideService.getPrimaryWorkspacePath())
                        addProperty("toolsAvailable", IdeBridge.availableTools())
                    }.toString())
                })
            })
        }
    }
}

class RunCommandTool : McpTool {
    override fun getName(): String = "runCommand"
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val command = args.get("command")?.asString.orEmpty()
        if (command.isBlank()) throw IllegalArgumentException("command required")
        val timeout = args.get("timeoutSeconds")?.asLong ?: 120L
        
        val result = ideService.runCommand(command, timeout)
        
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
    override suspend fun execute(args: JsonObject, ideService: IdeService): JsonObject {
        val message = args.get("message")?.asString.orEmpty()
        ideService.showMessage(message)
        return textResult("shown")
    }
}
