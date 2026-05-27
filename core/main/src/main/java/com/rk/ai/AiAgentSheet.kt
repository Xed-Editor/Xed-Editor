package com.rk.ai

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.BottomPanelMode
import com.rk.ai.agents.AiAgent
import com.rk.ai.session.AiSessionManager
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.filetree.drawerTabs
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.AgentCliSheet
import com.rk.tabs.editor.AgentSheetTerminal
import com.rk.terminal.TerminalViewModel
import com.rk.terminal.TerminalScreenInternal
import com.rk.terminal.changeSession
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedToolSheet(
    viewModel: MainViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    terminalViewModel: TerminalViewModel = viewModel(),
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity
    val colorScheme = MaterialTheme.colorScheme

    // Ensure terminal service is bound when the sheet is open
    DisposableEffect(Unit) {
        terminalViewModel.bindService(context)
        onDispose {
            terminalViewModel.unbindService(context)
        }
    }

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
    
    val aiSession = AiSessionManager.session
    val isAiRunning = aiSession?.isRunning == true
    var showTranscript by remember { mutableStateOf(false) }
    val transcript = viewModel.agentTranscript

    fun appendLog(text: String) {
        viewModel.agentTranscript =
            listOf(viewModel.agentTranscript, text)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
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
        val currentActivity = activity ?: return
        if (!forceRestart && extraArgs.isEmpty() && AiSessionManager.canReuseFor(workingDir)) {
            IdeBridge.setWorkspacePath(workingDir)
            appendLog("Reusing agent session: ${AiSessionManager.cwd}")
            return
        }
        AiSessionManager.stopSession()
        scope.launch(Dispatchers.Main) {
            val saved = withContext(Dispatchers.IO) { saveDirtyEditors() }
            AiSessionManager.startSession(currentActivity, viewModel, workingDir, extraArgs)
            if (saved > 0) appendLog("Synced $saved dirty editor file(s) before agent start.")
            appendLog("Agent running in sheet: $workingDir")
        }
    }

    fun sendToAgent(text: String) {
        if (text.isBlank()) return
        val runningSession = AiSessionManager.session
        if (runningSession?.isRunning == true && runningSession.emulator != null) {
            scope.launch(Dispatchers.IO) {
                val saved = saveDirtyEditors()
                withContext(Dispatchers.Main) {
                    if (saved > 0) appendLog("Synced $saved dirty editor file(s).")
                    runningSession.write("$text\r")
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
                val saved = saveDirtyEditors()
                withContext(Dispatchers.Main) { appendLog("Synced $saved dirty editor file(s).") }
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
                val health = if (alive) IdeBridge.healthCheck() else false
                val info = IdeBridge.getBridgeInfo()
                val workspacePath = IdeBridge.primaryWorkspacePath()
                appendLog(
                    buildString {
                        appendLine("Bridge status:")
                        appendLine("  running=$alive health=$health")
                        if (info != null) {
                            appendLine("  url=http://${info.host}:${info.port}")
                            appendLine("  token=${info.token.take(8)}...")
                        }
                        appendLine("  clients=${IdeBridge.connectedClients()}")
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

    LaunchedEffect(Unit) {
        val pendingPrompt = consumePendingPrompt()
        if (pendingPrompt != null) {
            handleInput(pendingPrompt)
        } else if (!AiSessionManager.canReuseFor(cwd.value)) {
            startAgent(cwd.value)
        }
    }

    LaunchedEffect(viewModel.agentPrompt, viewModel.showBottomPanel) {
        if (!viewModel.showBottomPanel) return@LaunchedEffect
        if (viewModel.bottomPanelMode == BottomPanelMode.AI) {
            consumePendingPrompt()?.let { handleInput(it) }
        }
    }

    val currentAgent = AiSessionManager.currentAgent
    val currentTab = viewModel.currentTab as? EditorTab
    val editor = currentTab?.editorState?.editor?.get()
    val selectedText = editor?.getSelectedText().orEmpty()

    AgentCliSheet(
        onDismissRequest = onDismissRequest,
        cwd = cwd.value,
        session = null,
        modifier = modifier,
        showTerminal = false,
        headerContent = {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                TabRow(
                    selectedTabIndex = if (viewModel.bottomPanelMode == BottomPanelMode.AI) 0 else 1,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[if (viewModel.bottomPanelMode == BottomPanelMode.AI) 0 else 1]),
                            color = colorScheme.primary,
                            height = 3.dp
                        )
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Tab(
                        selected = viewModel.bottomPanelMode == BottomPanelMode.AI,
                        onClick = { viewModel.bottomPanelMode = BottomPanelMode.AI },
                        icon = { XedIcon(com.rk.icons.Icon.DrawableRes(drawables.auto_fix), modifier = Modifier.size(20.dp)) },
                        text = { Text("AI Agent", style = MaterialTheme.typography.labelLarge) },
                        selectedContentColor = colorScheme.primary,
                        unselectedContentColor = colorScheme.onSurfaceVariant
                    )
                    Tab(
                        selected = viewModel.bottomPanelMode == BottomPanelMode.TERMINAL,
                        onClick = { viewModel.bottomPanelMode = BottomPanelMode.TERMINAL },
                        icon = { XedIcon(com.rk.icons.Icon.DrawableRes(drawables.terminal), modifier = Modifier.size(20.dp)) },
                        text = { Text("Terminal", style = MaterialTheme.typography.labelLarge) },
                        selectedContentColor = colorScheme.primary,
                        unselectedContentColor = colorScheme.onSurfaceVariant
                    )
                }

                if (viewModel.bottomPanelMode == BottomPanelMode.AI) {
                    var showAgentMenu by remember { mutableStateOf(false) }
                    StatusBar(
                        isRunning = isAiRunning,
                        cwd = cwd.value,
                        agent = currentAgent,
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
                }
            }
        },
        controls = {
            if (viewModel.bottomPanelMode == BottomPanelMode.AI) {
                val currentTab = viewModel.currentTab as? EditorTab
                val editor = currentTab?.editorState?.editor?.get()

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (editor?.canUndo() == true) {
                                editor.undo()
                                currentTab!!.editorState.updateUndoRedo()
                            }
                        },
                        enabled = editor?.canUndo() == true,
                        modifier = Modifier.size(32.dp)
                    ) {
                        XedIcon(com.rk.icons.Icon.DrawableRes(drawables.undo), contentDescription = "Undo", tint = if (editor?.canUndo() == true) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.38f))
                    }

                    IconButton(
                        onClick = {
                            if (editor?.canRedo() == true) {
                                editor.redo()
                                currentTab!!.editorState.updateUndoRedo()
                            }
                        },
                        enabled = editor?.canRedo() == true,
                        modifier = Modifier.size(32.dp)
                    ) {
                        XedIcon(com.rk.icons.Icon.DrawableRes(drawables.redo), contentDescription = "Redo", tint = if (editor?.canRedo() == true) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.38f))
                    }

                    IconButton(onClick = { startAgent(cwd.value, forceRestart = true) }, modifier = Modifier.size(32.dp)) {
                        XedIcon(com.rk.icons.Icon.DrawableRes(drawables.restart), contentDescription = "Restart", tint = colorScheme.onSurfaceVariant)
                    }

                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val saved = saveDirtyEditors()
                            withContext(Dispatchers.Main) { appendLog("Synced $saved dirty editor file(s).") }
                        }
                    }, modifier = Modifier.size(32.dp)) {
                        XedIcon(com.rk.icons.Icon.DrawableRes(drawables.save), contentDescription = "Sync", tint = colorScheme.onSurfaceVariant)
                    }

                    IconButton(onClick = {
                        AiSessionManager.stopSession()
                        appendLog("Agent stopped.")
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Stop", tint = colorScheme.error)
                    }
                }
            } else if (viewModel.bottomPanelMode == BottomPanelMode.TERMINAL) {
                var showSessionMenu by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { showSessionMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Menu, contentDescription = "Sessions", tint = colorScheme.onSurfaceVariant)
                        }
                        val service = terminalViewModel.sessionBinder?.getService()
                        DropdownMenu(expanded = showSessionMenu, onDismissRequest = { showSessionMenu = false }) {
                            service?.sessionList?.forEach { sessionId ->
                                DropdownMenuItem(
                                    text = { Text(sessionId, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        showSessionMenu = false
                                        val activity = terminalViewModel.terminalView?.context as? android.app.Activity
                                        if (activity is com.rk.activities.terminal.Terminal) {
                                            activity.changeSession(sessionId, terminalViewModel)
                                        } else {
                                            service.currentSession.value = sessionId
                                        }
                                    },
                                    leadingIcon = if (sessionId == service.currentSession.value) {
                                        { XedIcon(com.rk.icons.Icon.DrawableRes(drawables.auto_fix), modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("New Session", style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    showSessionMenu = false
                                    terminalViewModel.terminalView?.let {
                                        val client = com.rk.terminal.TerminalBackEnd(terminalViewModel)
                                        terminalViewModel.sessionBinder?.createSession(
                                            "main #${service?.sessionList?.size?.plus(1) ?: 1}",
                                            client,
                                            it.context as android.app.Activity,
                                        )
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    
                    IconButton(onClick = { /* TODO */ }, modifier = Modifier.size(32.dp)) {
                        XedIcon(com.rk.icons.Icon.DrawableRes(drawables.settings), contentDescription = "Settings", tint = colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        bottomBar = {
            if (viewModel.bottomPanelMode == BottomPanelMode.AI) {
                QuickActions(
                    isRunning = isAiRunning,
                    currentFile = cwd.value.split("/").lastOrNull() ?: "",
                    hasSelection = selectedText.isNotBlank(),
                    onAction = { handleInput(it) },
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (viewModel.bottomPanelMode) {
                BottomPanelMode.AI -> {
                    AgentSheetTerminal(session = aiSession, modifier = Modifier.fillMaxSize())
                }
                BottomPanelMode.TERMINAL -> {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        com.rk.terminal.TerminalPanel(
                            terminalViewModel = terminalViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(
    isRunning: Boolean,
    cwd: String,
    agent: AiAgent,
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
    val statusColor = if (isRunning) Color(0xFF4CAF50) else Color(0xFFEF5350)
    val bridgeClients = IdeBridge.connectedClients()
    val bridgeOnline = IdeBridge.isRunning()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Agent Status
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isRunning) "Active" else "Stopped",
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
            
            Spacer(Modifier.width(8.dp))

            // Bridge Status
            if (bridgeOnline) {
                val bridgeColor = if (bridgeClients > 0) Color(0xFF4CAF50) else Color(0xFFFFC107)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(bridgeColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(bridgeColor))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (bridgeClients > 0) "${bridgeClients} link" else "bridge",
                        color = bridgeColor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            // Agent Selector
            Box {
                Surface(
                    onClick = onToggleAgentMenu,
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.height(24.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = agent.displayName,
                            color = colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.onSecondaryContainer,
                        )
                    }
                }
                DropdownMenu(expanded = showAgentMenu, onDismissRequest = onToggleAgentMenu) {
                    availableAgents.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a.displayName, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onSelectAgent(a) },
                            leadingIcon = if (a == agent) {
                                { Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) } // Checkmark replacement
                            } else null,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            
            // Path display
            Text(
                text = cwd.split("/").lastOrNull()?.takeIf { it.isNotBlank() } ?: "/",
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            
            if (transcript.isNotBlank()) {
                IconButton(onClick = onToggleTranscript, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (showTranscript) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
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
                    val scrollState = rememberScrollState()
                    LaunchedEffect(transcript) {
                        if (showTranscript) scrollState.animateScrollTo(scrollState.maxValue)
                    }
                    Text(
                        text = transcript,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .verticalScroll(scrollState),
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
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isRunning) {
            Button(
                onClick = { onAction("/restart") },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text("Start Agent", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            // Action buttons as compact chips
            ActionChip(label = "Explain", onClick = { onAction(prompt("Explain the code")) }, color = colorScheme.secondaryContainer)
            ActionChip(label = "Bugs", onClick = { onAction(prompt("Find bugs and issues")) }, color = colorScheme.secondaryContainer)
            ActionChip(label = "Refactor", onClick = { onAction(prompt("Suggest improvements")) }, color = colorScheme.secondaryContainer)
            ActionChip(label = "Tests", onClick = { onAction(prompt("Add unit tests")) }, color = colorScheme.secondaryContainer)
            ActionChip(label = "Docs", onClick = { onAction(prompt("Write documentation")) }, color = colorScheme.secondaryContainer)
            
            Spacer(Modifier.width(8.dp))
            VerticalDivider(modifier = Modifier.height(24.dp), color = colorScheme.outlineVariant)
            Spacer(Modifier.width(8.dp) )

            ActionChip(label = "Sync", onClick = { onAction("/sync") }, color = colorScheme.surfaceVariant)
            ActionChip(label = "Refresh", onClick = { onAction("/refresh") }, color = colorScheme.surfaceVariant)
            ActionChip(label = "Export", onClick = { onAction("/export") }, color = colorScheme.surfaceVariant)
            ActionChip(label = "Stop", onClick = { onAction("/stop") }, color = colorScheme.errorContainer, labelColor = colorScheme.error)
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    onClick: () -> Unit,
    color: Color,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = color,
        modifier = Modifier.height(32.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
        }
    }
}
