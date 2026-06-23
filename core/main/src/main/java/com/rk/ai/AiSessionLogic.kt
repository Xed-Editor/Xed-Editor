package com.rk.ai

import android.app.Activity
import com.rk.activities.main.MainViewModel
import com.rk.tabs.editor.EditorTab
import com.termux.terminal.TerminalSession
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiSessionLogic(
    private val viewModel: MainViewModel,
    private val activity: Activity?,
    private val scope: CoroutineScope,
    private val cwd: () -> String,
    private val appendLog: (String) -> Unit,
) {
    suspend fun saveDirtyEditors(): Int {
        val dirtyTabs = viewModel.tabs.filterIsInstance<EditorTab>().filter { it.editorState.isDirty }
        withContext(Dispatchers.Main) {
            dirtyTabs.forEach { tab ->
                tab.editorState.editor.get()?.let { editor -> tab.editorState.content = editor.text }
            }
        }
        dirtyTabs.forEach { it.quickSave() }
        return dirtyTabs.size
    }

    fun refreshCleanEditors() {
        viewModel.tabs.filterIsInstance<EditorTab>()
            .filterNot { it.editorState.isDirty }
            .forEach { it.refresh() }
    }

    fun startAgent(workingDir: String = cwd(), extraArgs: List<String> = emptyList(), forceRestart: Boolean = false) {
        val currentActivity = activity ?: return
        if (!forceRestart && extraArgs.isEmpty() && AiProvider.sessionManager?.canReuseFor(workingDir) == true) {
            AiProvider.ideBridge?.setWorkspacePath(workingDir)
            appendLog("Reusing agent session: ${AiProvider.sessionManager?.cwd ?: "?"}")
            return
        }
        AiProvider.sessionManager?.stopSession()
        scope.launch(Dispatchers.Main) {
            val saved = withContext(Dispatchers.IO) { saveDirtyEditors() }
            AiProvider.sessionManager?.startSession(currentActivity, viewModel, workingDir, extraArgs)
            if (saved > 0) appendLog("Synced $saved dirty editor file(s) before agent start.")
            appendLog("Agent running in sheet: $workingDir")
        }
    }

    fun sendToAgent(text: String) {
        if (text.isBlank()) return
        val runningSession = AiProvider.sessionManager?.sessionState?.value as? TerminalSession
        if (runningSession?.isRunning == true && runningSession.emulator != null) {
            scope.launch(Dispatchers.IO) {
                val saved = saveDirtyEditors()
                withContext(Dispatchers.Main) {
                    if (saved > 0) appendLog("Synced $saved dirty editor file(s).")
                    runningSession.write("$text\r")
                }
            }
        } else {
            val dir = cwd()
            startAgent(dir, listOf("--prompt-interactive", text))
        }
    }

    fun handleInput(rawInput: String) {
        val input = rawInput.trim()
        if (input.isBlank()) return
        when (input) {
            "/sync" -> scope.launch(Dispatchers.IO) {
                val saved = saveDirtyEditors()
                withContext(Dispatchers.Main) { appendLog("Synced $saved dirty editor file(s).") }
            }
            "/refresh" -> {
                refreshCleanEditors()
                appendLog("Refreshed clean editor tabs from disk.")
            }
            "/restart" -> startAgent(forceRestart = true)
            "/stop" -> {
                AiProvider.sessionManager?.stopSession()
                appendLog("Agent stopped.")
            }
            "/export" -> exportSession()
            "/bridge", "/doctor" -> reportBridgeStatus()
            "/mcp" -> reportExternalMcpStatus()
            "/help" -> appendLog(
                buildString {
                    appendLine("AI sheet commands:")
                    appendLine("  /doctor  Check MCP bridge, clients, tools, and config paths")
                    appendLine("  /mcp     Check external MCP server status")
                    appendLine("  /sync    Save dirty editor tabs before agent work")
                    appendLine("  /refresh Refresh clean editor tabs from disk")
                    appendLine("  /restart Restart the selected AI agent")
                    appendLine("  /stop    Stop the current agent session")
                    appendLine("  /export  Share the current AI transcript")
                }
            )
            else -> sendToAgent(input)
        }
    }

    private fun reportExternalMcpStatus() {
        scope.launch(Dispatchers.IO) {
            val statusJson = AiProvider.ideBridge?.getExternalMcpStatus()
            if (statusJson == null) {
                withContext(Dispatchers.Main) { appendLog("MCP bridge not available. Use /doctor to start it.") }
                return@launch
            }
            val result = runCatching {
                com.google.gson.JsonParser.parseString(statusJson).asJsonObject
            }.getOrNull()
            if (result == null) {
                withContext(Dispatchers.Main) { appendLog("Failed to query external MCP status.") }
                return@launch
            }
            withContext(Dispatchers.Main) {
                appendLog(
                    buildString {
                        appendLine("External MCP server status:")
                        appendLine("  Servers: ${result.get("servers")?.asInt ?: 0}")
                        appendLine("  External tools: ${result.get("tools")?.asInt ?: 0}")
                        val arr = result.getAsJsonArray("status")
                        if (arr != null && arr.size() > 0) {
                            appendLine()
                            arr.forEach { el ->
                                val s = el.asJsonObject
                                appendLine("  ${s.get("name")?.asString ?: "?"}")
                                appendLine("    URL: ${s.get("url")?.asString ?: "?"}")
                                appendLine("    Reachable: ${s.get("reachable")?.asBoolean ?: false}")
                                appendLine("    Tools: ${s.get("toolCount")?.asInt ?: 0}")
                            }
                        } else {
                            appendLine("  No external MCP servers configured.")
                            appendLine()
                            appendLine("To add external MCP servers:")
                            appendLine("  1. Create .xed/mcp-servers.json in your project:")
                            appendLine("  { \"mcpServers\": { \"my-server\": {")
                            appendLine("      \"name\": \"my-server\",")
                            appendLine("      \"url\": \"http://host:port\",")
                            appendLine("      \"apiKey\": \"token\",")
                            appendLine("      \"enabled\": true")
                            appendLine("    }}}")
                            appendLine("  2. Or set them via Settings > AI > MCP Servers")
                            appendLine("  3. Run /mcp again after configuring")
                        }
                    }
                )
            }
        }
    }

    private fun reportBridgeStatus() {
        scope.launch(Dispatchers.Main) {
            val bridgeProvider = AiProvider.ideBridge
            val currentActivity = activity
            val dir = cwd()
            if (bridgeProvider != null && currentActivity != null && !bridgeProvider.isRunning()) {
                bridgeProvider.ensureStarted(viewModel, dir)
            } else {
                bridgeProvider?.setWorkspacePath(dir)
            }

            val alive = bridgeProvider?.isRunning() == true
            val health = if (alive) withContext(Dispatchers.IO) { bridgeProvider?.healthCheck() == true } else false
            val info = bridgeProvider?.getBridgeInfo()
            val workspacePath = bridgeProvider?.primaryWorkspacePath().orEmpty()
            val agent = AiProvider.sessionManager?.currentAgent
            val clients = bridgeProvider?.connectedClients() ?: 0
            val tools = bridgeProvider?.availableTools() ?: 0

            appendLog(
                buildString {
                    appendLine("AI bridge diagnostics:")
                    appendLine("  agent=${agent?.displayName ?: "AI"} (${agent?.name ?: "unknown"})")
                    appendLine("  running=$alive health=$health clients=$clients tools=$tools")
                    if (info != null) {
                        appendLine("  url=http://${info.host}:${info.port}")
                        appendLine("  token=${info.token.take(8)}...")
                    }
                    appendLine("  workspace=${workspacePath.ifBlank { dir }}")
                    appendLine()
                    appendLine("Expected config refresh:")
                    appendLine("  Gemini: ~/.gemini/settings.json")
                    appendLine("  OpenCode: ~/.config/opencode/opencode.json")
                    appendLine("  Codex: ~/.codex/config.toml")
                    appendLine("  Antigravity: ~/.gemini/config/mcp_config.json")
                    appendLine()
                    appendLine("Inside an agent terminal:")
                    if (workspacePath.isNotBlank()) {
                        appendLine("  source $workspacePath/.xed/ide.env")
                        appendLine("  cat $workspacePath/.xed/ide.json")
                    }
                    appendLine("  curl http://127.0.0.1:${info?.port ?: "?"}/health")
                    appendLine("  curl http://127.0.0.1:${info?.port ?: "?"}/mcp-info")
                    appendLine()
                    appendLine("External MCP:")
                    appendLine("  Config: ~/.xed/mcp-servers.json or Settings")
                    appendLine("  Servers: ${bridgeProvider?.connectedClients() ?: 0}")
                    appendLine("  External tools: ${bridgeProvider?.availableTools() ?: 0}")
                }
            )
        }
    }

    fun consumePendingPrompt(): String? {
        val pending = viewModel.agentPrompt.trim()
        if (pending.isBlank()) return null
        viewModel.agentPrompt = ""
        return pending
    }

    private fun exportSession() {
        val context = com.rk.utils.application ?: return
        val agentName = AiProvider.sessionManager?.currentAgent?.displayName ?: "AI"
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
        val transcript = viewModel.agentTranscript
        val markdown = buildString {
            appendLine("# AI Agent Session: $agentName")
            appendLine("**Date:** $timestamp")
            appendLine("**Workspace:** ${cwd()}")
            appendLine("**Agent:** $agentName")
            appendLine()
            appendLine("---")
            appendLine()
            if (transcript.isNotBlank()) {
                appendLine(transcript)
            } else {
                appendLine("*No conversation history*")
            }
        }

        try {
            val file = File(context.cacheDir, "agent-session-${System.currentTimeMillis()}.md")
            file.writeText(markdown, StandardCharsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Export Agent Session"))
        } catch (e: Exception) {
            com.rk.utils.toast("Export failed: ${e.message}")
        }
    }
}
