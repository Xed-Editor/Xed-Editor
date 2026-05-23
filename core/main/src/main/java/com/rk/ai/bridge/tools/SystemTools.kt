package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.IdeBridge
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import com.rk.utils.application

class GetIdeInfoTool : BaseMcpTool() {
    override val name: String = "getIdeInfo"
    override val description: String = "Returns IDE status and a pointer to system guidelines."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val info = IdeBridge.getBridgeInfo()
        val version = runCatching { application?.packageManager?.getPackageInfo(application!!.packageName, 0)?.versionName }.getOrNull().orEmpty()
        val text = JsonObject().apply {
            addProperty("name", "Xed Editor")
            addProperty("version", version)
            addProperty("bridgeRunning", IdeBridge.isRunning())
            addProperty("workspace", context.ideService.getPrimaryWorkspacePath())
            addProperty("guidelines", "Call 'getGuidelines' for recommended high-performance workflows.")
        }.toString()
        return resultText(text)
    }
}

class RunCommandTool : BaseMcpTool() {
    override val name: String = "runCommand"
    override val description: String = "Runs a shell command in the terminal environment."
    override val requiredParams: Map<String, String> = mapOf("command" to "string")
    override val optionalParams: Map<String, String> = mapOf("timeoutSeconds" to "number")
    override val timeoutMs: Long = 120_000L
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val command = requireString(args, "command")
        val timeout = optionalLong(args, "timeoutSeconds", 120L)
        val result = context.ideService.runCommand(command, timeout)
        val text = buildString {
            if (result.output.isNotBlank()) appendLine(result.output)
            if (result.error.isNotBlank()) appendLine(result.error)
            append("exit ${result.exitCode}")
            if (result.timedOut) append(" (timed out)")
        }
        return resultText(enforceOutputLimit(text))
    }
}

class ShowMessageTool : BaseMcpTool() {
    override val name: String = "showMessage"
    override val description: String = "Displays a short toast notification message."
    override val requiredParams: Map<String, String> = mapOf("message" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val message = requireString(args, "message")
        context.ideService.showMessage(message)
        return resultText("shown")
    }
}

class GetProjectConfigTool : BaseMcpTool() {
    override val name: String = "getProjectConfig"
    override val description: String = "Detects project configuration."
    override val optionalParams: Map<String, String> = mapOf("path" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val path = optionalString(args, "path").ifBlank { context.ideService.getPrimaryWorkspacePath() }
        val config = context.ideService.getProjectConfig(path)
        return resultJson(config)
    }
}
