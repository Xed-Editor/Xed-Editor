package com.rk.ai

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainViewModel
import com.rk.ai.agents.AiAgent
import com.rk.ai.session.AiSessionManager
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.filetree.drawerTabs
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.AgentCliSheet
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_TRANSCRIPT_LENGTH = 50_000
private const val MAX_SNIPPET_LINES = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentSheet(
    viewModel: MainViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity
    val colorScheme = MaterialTheme.colorScheme

    fun terminalHomeDir(): String =
        if (Settings.sandbox) "/home" else sandboxHomeDir().absolutePath

    fun currentProjectDir(): String {
        val activeTab = viewModel.currentTab as? EditorTab
        activeTab?.projectRoot?.getAbsolutePath()?.takeIf { it.isNotBlank() && it.startsWith("/") }?.let { return it }
        val projectDir = ((currentDrawerTab as? FileTreeTab)?.root as? FileWrapper)?.getAbsolutePath()
            ?: drawerTabs.filterIsInstance<FileTreeTab>().mapNotNull { it.root as? FileWrapper }.firstOrNull()?.getAbsolutePath()
        if (projectDir != null && projectDir.isNotBlank()) return projectDir
        val path = activeTab?.file?.getAbsolutePath()?.takeIf { it.startsWith("/") } ?: return terminalHomeDir()
        val localFile = File(path)
        if (localFile.isDirectory) return localFile.absolutePath
        return localFile.parent?.takeIf { it.isNotBlank() } ?: terminalHomeDir()
    }

    val cwd = remember { mutableStateOf(currentProjectDir()) }
    LaunchedEffect(viewModel.currentTab, currentDrawerTab) {
        cwd.value = currentProjectDir()
    }
    val session = AiSessionManager.session
    val isRunning = session?.isRunning == true
    var showTranscript by remember { mutableStateOf(false) }
    val transcript = viewModel.agentTranscript

    fun appendLog(text: String) {
        val current = viewModel.agentTranscript
        val updated = if (current.isNotBlank()) "$current\n\n$text" else text
        viewModel.agentTranscript = updated.takeLast(MAX_TRANSCRIPT_LENGTH)
    }

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

    fun startAgent(workingDir: String = cwd.value, extraArgs: List<String> = emptyList(), forceRestart: Boolean = false) {
        val currentActivity = activity ?: run {
            appendLog("Error: Activity reference lost")
            return
        }
        if (!forceRestart && extraArgs.isEmpty() && AiSessionManager.canReuseFor(workingDir)) {
            IdeBridge.setWorkspacePath(workingDir)
            appendLog("Reusing agent session: ${AiSessionManager.cwd}")
            return
        }
        AiSessionManager.stopSession()
        scope.launch(Dispatchers.Main) {
            try {
                val saved = withContext(Dispatchers.IO) { saveDirtyEditors() }
                AiSessionManager.startSession(currentActivity, viewModel, workingDir, extraArgs)
                if (saved > 0) appendLog("Synced $saved dirty editor file(s) before agent start.")
                appendLog("Agent running in sheet: $workingDir")
            } catch (e: Exception) {
                appendLog("Failed to start agent: ${e.message}")
            }
        }
    }

    fun sendToAgent(text: String) {
        if (text.isBlank()) return
        val runningSession = AiSessionManager.session
        if (runningSession?.isRunning == true && runningSession.emulator != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val saved = saveDirtyEditors()
                    withContext(Dispatchers.Main) {
                        if (saved > 0) appendLog("Synced $saved dirty editor file(s).")
                        runningSession.write("$text\r")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { appendLog("Error sending: ${e.message}") }
                }
            }
        } else {
            val dir = cwd.value
            startAgent(dir, listOf("--prompt-interactive", text))
        }
    }

    fun handleInput(rawInput: String) {
        val input = rawInput.trim()
        if (input.isBlank()) return
        when (input) {
            "/sync" -> scope.launch(Dispatchers.IO) {
                try {
                    val saved = saveDirtyEditors()
                    withContext(Dispatchers.Main) { appendLog("Synced $saved dirty editor file(s).") }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { appendLog("Sync failed: ${e.message}") }
                }
            }
            "/refresh" -> {
                refreshCleanEditors()
                appendLog("Refreshed clean editor tabs from disk.")
            }
            "/restart" -> startAgent(forceRestart = true)
            "/stop" -> {
                AiSessionManager.stopSession()
                appendLog("Agent stopped.")
            }
            "/export" -> exportSession(viewModel, cwd.value)
            "/bridge" -> {
                val alive = IdeBridge.isRunning()
                val info = IdeBridge.getBridgeInfo()
                val workspacePath = IdeBridge.primaryWorkspacePath()
                val (mcpOk, mcpStatus) = if (alive) IdeBridge.checkMcpConnection() else false to "bridge not running"
                appendLog(
                    buildString {
                        appendLine("Bridge status:")
                        appendLine("  running=$alive")
                        appendLine("  mcp=$mcpOk")
                        if (info != null) {
                            appendLine("  url=http://${info.host}:${info.port}")
                            appendLine("  token=${info.token.take(8)}...")
                        }
                        appendLine("  clients=${IdeBridge.connectedClients()}")
                        appendLine("  tools=${IdeBridge.availableTools()}")
                        appendLine("  workspace=$workspacePath")
                        if (!mcpOk) appendLine("  mcpStatus=$mcpStatus")
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

    LaunchedEffect(Unit) {
        val pendingPrompt = consumePendingPrompt()
        if (pendingPrompt != null) {
            handleInput(pendingPrompt)
        } else if (!AiSessionManager.canReuseFor(cwd.value)) {
            startAgent(cwd.value)
        }
    }

    LaunchedEffect(viewModel.agentPrompt, viewModel.showAiSheet) {
        if (!viewModel.showAiSheet) return@LaunchedEffect
        consumePendingPrompt()?.let { handleInput(it) }
    }

    var showAgentMenu by remember { mutableStateOf(false) }

    val currentAgent = AiSessionManager.currentAgent
    val currentTab = viewModel.currentTab as? EditorTab
    val editor = currentTab?.editorState?.editor?.get()
    val selectedText = editor?.getSelectedText().orEmpty()

    AgentCliSheet(
        onDismissRequest = onDismissRequest,
        cwd = cwd.value,
        session = session,
        modifier = modifier,
        headerContent = {
            StatusBar(
                isRunning = isRunning,
                cwd = cwd.value,
                agent = currentAgent,
                model = Settings.ai_model.ifEmpty { currentAgent.defaultModel },
                availableAgents = AiSessionManager.availableAgents(),
                showAgentMenu = showAgentMenu,
                onToggleAgentMenu = { showAgentMenu = !showAgentMenu },
                onSelectAgent = { agent ->
                    showAgentMenu = false
                    if (agent != AiSessionManager.currentAgent) {
                        AiSessionManager.switchAgent(agent.name)
                        startAgent(cwd.value, forceRestart = true)
                    }
                },
                transcript = transcript,
                showTranscript = showTranscript,
                onToggleTranscript = { showTranscript = !showTranscript },
                onClearTranscript = {
                    viewModel.agentTranscript = ""
                    showTranscript = false
                },
            )
        },
        controls = {
            val currentTab = viewModel.currentTab as? EditorTab
            val editor = currentTab?.editorState?.editor?.get()

            IconButton(
                onClick = {
                    if (editor?.canUndo() == true) {
                        editor.undo()
                        currentTab!!.editorState.updateUndoRedo()
                    }
                },
                enabled = editor?.canUndo() == true
            ) {
                XedIcon(com.rk.icons.Icon.DrawableRes(drawables.undo), contentDescription = "Undo")
            }

            IconButton(
                onClick = {
                    if (editor?.canRedo() == true) {
                        editor.redo()
                        currentTab!!.editorState.updateUndoRedo()
                    }
                },
                enabled = editor?.canRedo() == true
            ) {
                XedIcon(com.rk.icons.Icon.DrawableRes(drawables.redo), contentDescription = "Redo")
            }

            IconButton(onClick = { startAgent(cwd.value, forceRestart = true) }) {
                XedIcon(com.rk.icons.Icon.DrawableRes(drawables.restart), contentDescription = "Restart")
            }

            IconButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        val saved = saveDirtyEditors()
                        withContext(Dispatchers.Main) { appendLog("Synced $saved dirty editor file(s).") }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { appendLog("Sync failed: ${e.message}") }
                    }
                }
            }) {
                XedIcon(com.rk.icons.Icon.DrawableRes(drawables.save), contentDescription = "Sync")
            }

            IconButton(onClick = {
                AiSessionManager.stopSession()
                appendLog("Agent stopped.")
            }, enabled = isRunning) {
                Icon(Icons.Outlined.Close, contentDescription = "Stop", tint = colorScheme.error.copy(alpha = 0.7f))
            }

            val connectionStatus = AiSessionManager.connectionStatus
            val canReconnect = connectionStatus == AiSessionManager.ConnectionStatus.Error ||
                    connectionStatus == AiSessionManager.ConnectionStatus.Disconnected
            IconButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            appendLog("Attempting reconnection...")
                            val success = AiSessionManager.reconnect(activity!!, viewModel)
                            appendLog(if (success) "Reconnected successfully" else "Reconnection failed: ${AiSessionManager.lastError}")
                        } catch (e: Exception) {
                            appendLog("Reconnection error: ${e.message}")
                        }
                    }
                },
                enabled = canReconnect && activity != null
            ) {
                XedIcon(com.rk.icons.Icon.DrawableRes(drawables.refresh), contentDescription = "Reconnect")
            }
        },
        bottomBar = {
            QuickActions(
                isRunning = isRunning,
                currentFile = cwd.value.split("/").lastOrNull() ?: "",
                hasSelection = selectedText.isNotBlank(),
                onAction = { handleInput(it) },
            )
        },
    )
}

@Composable
private fun StatusBar(
    isRunning: Boolean,
    cwd: String,
    agent: AiAgent,
    model: String,
    availableAgents: List<AiAgent>,
    showAgentMenu: Boolean,
    onToggleAgentMenu: () -> Unit,
    onSelectAgent: (AiAgent) -> Unit,
    transcript: String,
    showTranscript: Boolean,
    onToggleTranscript: () -> Unit,
    onClearTranscript: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val session = com.rk.ai.session.AiSessionManager.session
    val connectionStatus = com.rk.ai.session.AiSessionManager.connectionStatus
    val lastError = com.rk.ai.session.AiSessionManager.lastError
    val dotColor = when {
        !isRunning -> Color(0xFFEF5350)
        connectionStatus == com.rk.ai.session.AiSessionManager.ConnectionStatus.Error -> Color(0xFFFFC107)
        connectionStatus == com.rk.ai.session.AiSessionManager.ConnectionStatus.Reconnecting -> Color(0xFFFFC107)
        else -> Color(0xFF4CAF50)
    }
    val bridgeClients = IdeBridge.connectedClients()
    val bridgeOnline = IdeBridge.isRunning()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (lastError != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                color = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Error: $lastError",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFC62828),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = when {
                    !isRunning -> "Stopped"
                    connectionStatus == com.rk.ai.session.AiSessionManager.ConnectionStatus.Connecting -> "Connecting..."
                    connectionStatus == com.rk.ai.session.AiSessionManager.ConnectionStatus.Reconnecting -> "Reconnecting..."
                    connectionStatus == com.rk.ai.session.AiSessionManager.ConnectionStatus.Error -> "Error"
                    else -> "Running"
                },
                color = dotColor,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(6.dp))

            if (bridgeOnline) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (bridgeClients > 0) Color(0xFF4CAF50) else Color(0xFFFFC107))
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (bridgeClients > 0) "${bridgeClients} client" else "bridge",
                    color = if (bridgeClients > 0) Color(0xFF4CAF50) else Color(0xFFFFC107),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(6.dp))
            }

            Box {
                Surface(
                    onClick = onToggleAgentMenu,
                    shape = RoundedCornerShape(4.dp),
                    color = colorScheme.primaryContainer,
                    modifier = Modifier.height(22.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = agent.displayName,
                            color = colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Switch agent",
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.onPrimaryContainer,
                        )
                    }
                }
                DropdownMenu(expanded = showAgentMenu, onDismissRequest = onToggleAgentMenu) {
                    availableAgents.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a.displayName, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onSelectAgent(a) },
                            leadingIcon = if (a == agent) {
                                { Text("\u2713", style = MaterialTheme.typography.bodySmall) }
                            } else null,
                        )
                    }
                }
            }

            if (model.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = model,
                    color = colorScheme.tertiary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(colorScheme.tertiaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = cwd.split("/").lastOrNull()?.takeIf { it.isNotBlank() } ?: "/",
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .background(colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (transcript.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onClearTranscript, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onToggleTranscript, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (showTranscript) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (showTranscript) "Hide log" else "Show log",
                        modifier = Modifier.size(20.dp),
                        tint = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showTranscript && transcript.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .padding(bottom = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.surfaceContainerHigh,
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Transcript", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text(
                        text = transcript,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun exportSession(viewModel: MainViewModel, cwd: String) {
    val context = com.rk.utils.application ?: return
    val agentName = AiSessionManager.currentAgent.displayName
    val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
    val transcript = viewModel.agentTranscript
    val markdown = buildString {
        appendLine("# AI Agent Session: $agentName")
        appendLine("**Date:** $timestamp")
        appendLine("**Workspace:** $cwd")
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
        val file = java.io.File(context.cacheDir, "agent-session-${System.currentTimeMillis()}.md")
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

@Composable
private fun QuickActions(
    isRunning: Boolean,
    currentFile: String,
    hasSelection: Boolean,
    onAction: (String) -> Unit,
) {
    fun prompt(text: String) = buildString {
        append(text)
        if (currentFile.isNotBlank()) append(" in $currentFile")
        if (hasSelection) append(" (selected code provided as context)")
    }

    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!isRunning) {
            AssistChip(
                onClick = { onAction("/restart") },
                label = { Text("Start Agent", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
        } else {
            AssistChip(
                onClick = { onAction("/stop") },
                label = { Text("Stop", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction("/sync") },
                label = { Text("Sync Files", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction(prompt("Explain the code")) },
                label = { Text("Explain", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction(prompt("Find bugs and issues")) },
                label = { Text("Find Bugs", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction(prompt("Suggest improvements")) },
                label = { Text("Refactor", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction(prompt("Add unit tests")) },
                label = { Text("Add Tests", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction(prompt("Write documentation")) },
                label = { Text("Document", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction("/export") },
                label = { Text("Export", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction("/refresh") },
                label = { Text("Refresh", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}