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
            "/bridge" -> {
                val alive = AiProvider.ideBridge?.isRunning() == true
                val health = if (alive) (AiProvider.ideBridge?.healthCheck() == true) else false
                val info = AiProvider.ideBridge?.getBridgeInfo()
                val workspacePath = AiProvider.ideBridge?.primaryWorkspacePath() ?: ""
                appendLog(
                    buildString {
                        appendLine("Bridge status:")
                        appendLine("  running=$alive health=$health")
                        if (info != null) {
                            appendLine("  url=http://${info.host}:${info.port}")
                            appendLine("  token=${info.token.take(8)}...")
                        }
                        appendLine("  clients=${AiProvider.ideBridge?.connectedClients() ?: 0}")
                        appendLine("  workspace=$workspacePath")
                        appendLine()
                        appendLine("Inside agent terminal, run:")
                        if (workspacePath.isNotBlank()) {
                            appendLine("  source $workspacePath/.xed/ide.env")
                            appendLine("  cat $workspacePath/.xed/ide.json")
                        }
                        appendLine("  curl http://127.0.0.1:${info?.port ?: "?"}/health")
                    }
                )
            }
            else -> sendToAgent(input)
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
