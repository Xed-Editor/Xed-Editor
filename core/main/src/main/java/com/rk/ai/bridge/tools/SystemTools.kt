package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.IdeBridge
import com.rk.ai.service.IdeService
import com.rk.utils.application

class GetIdeInfoTool : BaseMcpTool() {
    override fun getName(): String = "getIdeInfo"
    override fun getDescription(): String = "Returns IDE status and a pointer to system guidelines."
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val info = IdeBridge.getBridgeInfo()
        val version = runCatching { application?.packageManager?.getPackageInfo(application!!.packageName, 0)?.versionName }.getOrNull().orEmpty()
        val text = JsonObject().apply {
            addProperty("name", "Xed Editor")
            addProperty("version", version)
            addProperty("bridgeRunning", IdeBridge.isRunning())
            addProperty("workspace", ideService.getPrimaryWorkspacePath())
            addProperty("guidelines", "Call 'getGuidelines' for recommended high-performance workflows.")
        }.toString()
        return textResult(text)
    }
}

class RunCommandTool : BaseMcpTool() {
    override fun getName(): String = "runCommand"
    override fun getDescription(): String = "Runs a shell command in the terminal environment."
    override fun getRequiredParams(): Map<String, String> = mapOf("command" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("timeoutSeconds" to "number")
    override fun getTimeoutMs(): Long = 120_000L
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val command = requireString(args, "command")
        val timeout = optionalLong(args, "timeoutSeconds", 120L)
        val result = ideService.runCommand(command, timeout)
        val text = buildString {
            if (result.output.isNotBlank()) appendLine(result.output)
            if (result.error.isNotBlank()) appendLine(result.error)
            append("exit ${result.exitCode}")
            if (result.timedOut) append(" (timed out)")
        }
        return textResult(text)
    }

    private fun optionalLong(args: JsonObject, name: String, default: Long): Long {
        return args.get(name)?.takeIf { it.isJsonPrimitive }?.asLong ?: default
    }
}

class ShowMessageTool : BaseMcpTool() {
    override fun getName(): String = "showMessage"
    override fun getDescription(): String = "Displays a short toast notification message."
    override fun getRequiredParams(): Map<String, String> = mapOf("message" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val message = requireString(args, "message")
        ideService.showMessage(message)
        return textResult("shown")
    }
}

class GetProjectConfigTool : BaseMcpTool() {
    override fun getName(): String = "getProjectConfig"
    override fun getDescription(): String = "Detects project configuration."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override suspend fun executeValidated(args: JsonObject, ideService: IdeService): JsonObject {
        val path = optionalString(args, "path").ifBlank { ideService.getPrimaryWorkspacePath() }
        val config = ideService.getProjectConfig(path)
        return jsonResult(config)
    }
}
