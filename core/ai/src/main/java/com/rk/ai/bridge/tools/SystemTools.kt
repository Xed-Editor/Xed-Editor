package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.IdeBridge
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult
import com.rk.utils.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetIdeInfoTool : BaseMcpTool() {
    override fun getCategory(): String = "System"
    override fun getName(): String = "getIdeInfo"
    override fun getDescription(): String = "Returns IDE status and a pointer to system guidelines."
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
        return McpToolResult.success(text)
    }
}

class RunCommandTool : BaseMcpTool() {
    override fun getCategory(): String = "Terminal"
    override fun getName(): String = "runCommand"
    override fun getDescription(): String = "Runs a shell command in the terminal environment. PREFER NATIVE MCP TOOLS instead: use readFile/cat for reading, searchCode/grep for search, findFiles/glob for file find, head for head, tail for tail, wc for word count, stat for metadata, listFiles/ls for directory listing. Only use runCommand for compiling, running, or package installs that have no native tool."
    override fun getRequiredParams(): Map<String, String> = mapOf("command" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf("timeoutSeconds" to "number")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "command" to "Shell command to execute"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "timeoutSeconds" to "Timeout in seconds (default: 120)"
    )
    override fun getTimeoutMs(): Long = 120_000L
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
        return McpToolResult.success(text)
    }
}

class ShowMessageTool : BaseMcpTool() {
    override fun getCategory(): String = "System"
    override fun getName(): String = "showMessage"
    override fun getDescription(): String = "Displays a short toast notification message."
    override fun getRequiredParams(): Map<String, String> = mapOf("message" to "string")
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "message" to "Message text to display"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val message = requireString(args, "message")
        context.ideService.showMessage(message)
        return McpToolResult.success("shown")
    }
}

class GetProjectConfigTool : BaseMcpTool() {
    override fun getCategory(): String = "Project"
    override fun getName(): String = "getProjectConfig"
    override fun getDescription(): String = "Detects project configuration."
    override fun getOptionalParams(): Map<String, String> = mapOf("path" to "string")
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "path" to "Project path (default: workspace root)"
    )
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val path = optionalString(args, "path").ifBlank { context.ideService.getPrimaryWorkspacePath() }
        val config = context.ideService.getProjectConfig(path)
        return McpToolResult.success(config.toString())
    }
}

class GetEnvironmentTool : BaseMcpTool() {
    override fun getCategory(): String = "System"
    override fun getName(): String = "getEnvironment"
    override fun getDescription(): String = "Returns system and sandbox environment variables."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = buildJsonResult {
        System.getenv().forEach { (k, v) -> addProperty(k, v) }
    }
}

class GetClipboardTool : BaseMcpTool() {
    override fun getCategory(): String = "System"
    override fun getName(): String = "getClipboard"
    override fun getDescription(): String = "Returns the current device clipboard content."
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(kotlinx.coroutines.Dispatchers.Main) {
        val cm = application?.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val text = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        McpToolResult.success(text)
    }
}

class WriteToClipboardTool : BaseMcpTool() {
    override fun getCategory(): String = "System"
    override fun getName(): String = "writeToClipboard"
    override fun getDescription(): String = "Sets the device clipboard content."
    override fun getRequiredParams(): Map<String, String> = mapOf("text" to "string")
    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult = withContext(kotlinx.coroutines.Dispatchers.Main) {
        val text = requireString(args, "text")
        val cm = application?.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("Xed AI", text))
        McpToolResult.success("Copied to clipboard.")
    }
}
