package com.rk.ai

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.BottomPanelMode
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
import com.rk.tabs.editor.AgentSheetTerminal
import com.rk.terminal.TerminalViewModel
import com.rk.terminal.changeTerminalSession
import com.rk.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.virtualkeys.VirtualKeysView
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
    
    val aiSession = AiProvider.sessionManager?.sessionState?.value
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
        val runningSession = AiProvider.sessionManager?.sessionState?.value
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
                AiProvider.sessionManager?.stopSession()
                appendLog("Agent stopped.")
            }
            "/export" -> exportSession(viewModel, cwd.value)
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

    LaunchedEffect(Unit) {
        val pendingPrompt = consumePendingPrompt()
        if (pendingPrompt != null) {
            handleInput(pendingPrompt)
        } else if (AiProvider.sessionManager?.canReuseFor(cwd.value) != true) {
            startAgent(cwd.value)
        }
    }

    LaunchedEffect(viewModel.agentPrompt, viewModel.showBottomPanel) {
        if (!viewModel.showBottomPanel) return@LaunchedEffect
        if (viewModel.bottomPanelMode == BottomPanelMode.AI) {
            consumePendingPrompt()?.let { handleInput(it) }
        }
    }

    val currentAgent = AiProvider.sessionManager?.currentAgent
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
                    icon = {
                        Icon(
                            Icons.Outlined.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = {
                        Text(
                            "AI Agent",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (viewModel.bottomPanelMode == BottomPanelMode.AI) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    selectedContentColor = colorScheme.primary,
                    unselectedContentColor = colorScheme.onSurfaceVariant,
                )
                Tab(
                    selected = viewModel.bottomPanelMode == BottomPanelMode.TERMINAL,
                    onClick = { viewModel.bottomPanelMode = BottomPanelMode.TERMINAL },
                    icon = {
                        BadgedBox(
                            badge = {
                                val sessionCount = terminalViewModel.sessionBinder?.getService()?.sessionList?.size ?: 0
                                if (sessionCount > 1) {
                                    Badge(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary,
                                    ) {
                                        Text(sessionCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Outlined.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    text = {
                        Text(
                            "Terminal",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (viewModel.bottomPanelMode == BottomPanelMode.TERMINAL) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    selectedContentColor = colorScheme.primary,
                    unselectedContentColor = colorScheme.onSurfaceVariant,
                )
            }
        },
        controls = {
            if (viewModel.bottomPanelMode == BottomPanelMode.AI) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        onClick = {
                            if (editor?.canUndo() == true) {
                                editor.undo()
                                currentTab?.editorState?.updateUndoRedo()
                            }
                        },
                        enabled = editor?.canUndo() == true,
                        modifier = Modifier.size(30.dp)
                    ) {
                        XedIcon(com.rk.icons.Icon.DrawableRes(drawables.undo), modifier = Modifier.size(18.dp), tint = if (editor?.canUndo() == true) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.38f))
                    }

                    FilledTonalIconButton(
                        onClick = {
                            if (editor?.canRedo() == true) {
                                editor.redo()
                                currentTab?.editorState?.updateUndoRedo()
                            }
                        },
                        enabled = editor?.canRedo() == true,
                        modifier = Modifier.size(30.dp)
                    ) {
                        XedIcon(com.rk.icons.Icon.DrawableRes(drawables.redo), modifier = Modifier.size(18.dp), tint = if (editor?.canRedo() == true) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.38f))
                    }

                    FilledTonalIconButton(onClick = { startAgent(cwd.value, forceRestart = true) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Restart", modifier = Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant)
                    }

                    FilledTonalIconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val saved = saveDirtyEditors()
                            withContext(Dispatchers.Main) { appendLog("Synced $saved dirty editor file(s).") }
                        }
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Save, contentDescription = "Sync", modifier = Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant)
                    }
                }
            } else if (viewModel.bottomPanelMode == BottomPanelMode.TERMINAL) {
                var showSessionMenu by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        FilledTonalIconButton(onClick = { showSessionMenu = true }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Menu, contentDescription = "Sessions", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        val service = terminalViewModel.sessionBinder?.getService()
                        DropdownMenu(expanded = showSessionMenu, onDismissRequest = { showSessionMenu = false }) {
                            service?.sessionList?.forEach { sessionId ->
                                DropdownMenuItem(
                                    text = { Text(sessionId, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        showSessionMenu = false
                                        terminalViewModel.terminalView?.let { termView ->
                                            val activity = termView.context as? android.app.Activity
                                            if (activity != null) {
                                                scope.launch {
                                                    com.rk.terminal.changeTerminalSession(sessionId, terminalViewModel, activity)
                                                }
                                            }
                                        }
                                    },
                                    leadingIcon = if (sessionId == service.currentSession.value) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                                    } else null
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("New Session", style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    showSessionMenu = false
                                    terminalViewModel.terminalView?.let { tv ->
                                        val activity = tv.context as? android.app.Activity ?: return@let
                                        val client = com.rk.terminal.TerminalBackEnd(terminalViewModel)
                                        val sessionBinder = terminalViewModel.sessionBinder ?: return@let
                                        val activeTab = viewModel.currentTab as? EditorTab
                                        val activeFile = activeTab?.file?.getAbsolutePath() ?: ""
                                        val activeProject = activeTab?.projectRoot?.getAbsolutePath() ?: ""

                                        scope.launch(Dispatchers.IO) {
                                            sessionBinder.createSession(
                                                "main #${service?.sessionList?.size?.plus(1) ?: 1}",
                                                client,
                                                activity,
                                                activeFile = activeFile,
                                                activeProject = activeProject
                                            )
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    
                    FilledTonalIconButton(onClick = {
                        val ctx = context
                        android.content.Intent(ctx, com.rk.activities.settings.SettingsActivity::class.java).apply {
                            putExtra("route", com.rk.activities.settings.SettingsRoutes.TerminalSettings.route)
                            ctx.startActivity(this)
                        }
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        bottomBar = {
            UnifiedCommandBar(
                mode = viewModel.bottomPanelMode,
                isAiRunning = isAiRunning,
                cwd = cwd.value,
                agent = currentAgent,
                transcript = transcript,
                showTranscript = showTranscript,
                onToggleTranscript = { showTranscript = !showTranscript },
                onClearTranscript = { viewModel.agentTranscript = "" },
                onAction = { handleInput(it) },
                terminalViewModel = terminalViewModel,
                hasSelection = selectedText.isNotBlank(),
                currentFile = cwd.value.split("/").lastOrNull() ?: ""
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (viewModel.bottomPanelMode) {
                BottomPanelMode.AI -> {
                    if (aiSession != null && isAiRunning) {
                        AgentSheetTerminal(session = aiSession, modifier = Modifier.fillMaxSize(), showKeys = false)
                    } else {
                        AgentEmptyState(
                            isRunning = isAiRunning,
                            agentName = currentAgent.displayName,
                            onStart = { startAgent(cwd.value, forceRestart = true) },
                        )
                    }
                }
                BottomPanelMode.TERMINAL -> {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        com.rk.terminal.TerminalPanel(
                            terminalViewModel = terminalViewModel,
                            showKeys = false,
                            initialCwd = viewModel.terminalCwd,
                        )
                        // Clear the terminalCwd after it's been used to avoid repeated session creation
                        LaunchedEffect(viewModel.terminalCwd) {
                            if (viewModel.terminalCwd != null) {
                                viewModel.terminalCwd = null
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentEmptyState(
    isRunning: Boolean,
    agentName: String,
    onStart: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.surface.copy(alpha = 0.5f),
                        colorScheme.surfaceContainerLowest,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = colorScheme.primaryContainer.copy(alpha = 0.5f),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "$agentName Ready",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurface,
                )
                Text(
                    "Tap start to begin an AI-powered session",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
            ) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start Agent", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun UnifiedCommandBar(
    mode: BottomPanelMode,
    isAiRunning: Boolean,
    cwd: String,
    agent: AiProvider.AgentInfo?,
    transcript: String,
    showTranscript: Boolean,
    onToggleTranscript: () -> Unit,
    onClearTranscript: () -> Unit,
    onAction: (String) -> Unit,
    terminalViewModel: TerminalViewModel,
    hasSelection: Boolean,
    currentFile: String,
) {
    var showAgentMenu by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val availableAgents = AiProvider.sessionManager?.availableAgents() ?: emptyList()

    Column(modifier = Modifier.fillMaxWidth().background(colorScheme.surfaceContainerHighest)) {
        // Row 1: Status bar - common for both modes
        StatusBar(
            mode = mode,
            isRunning = isAiRunning,
            cwd = cwd,
            agent = agent,
            availableAgents = availableAgents,
            showAgentMenu = showAgentMenu,
            onToggleAgentMenu = { showAgentMenu = !showAgentMenu },
            onSelectAgent = { selectedAgent ->
                showAgentMenu = false
                val current = AiProvider.sessionManager?.currentAgent
                if (selectedAgent.name != current?.name) {
                    AiProvider.sessionManager?.switchAgent(selectedAgent.name)
                    onAction("/restart")
                }
            },
            transcript = transcript,
            showTranscript = showTranscript,
            onToggleTranscript = onToggleTranscript,
            onClearTranscript = onClearTranscript,
            terminalViewModel = terminalViewModel,
        )

        // Row 2: Bottom wire (virtual keys) - common for both modes
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
        AndroidView<VirtualKeysView>(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            factory = { ctx ->
                VirtualKeysView(ctx, null).apply {
                    setButtonTextColor(colorScheme.onSurface.toArgb())
                    setBackgroundColor(colorScheme.surfaceContainerHighest.toArgb())
                    runCatching {
                        val info = VirtualKeysInfo(
                            Settings.terminal_extra_keys,
                            "",
                            VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                        )
                        reload(info)
                    }
                    terminalViewModel.virtualKeysView = this
                }
            },
            update = { keys ->
                val session = if (mode == BottomPanelMode.AI) {
                    AiProvider.sessionManager?.sessionState?.value
                } else {
                    terminalViewModel.terminalView?.mTermSession
                }
                keys.setVirtualKeysViewClient(session?.let { VirtualKeysListener(it) })
                keys.setButtonTextColor(colorScheme.onSurface.toArgb())
                keys.setBackgroundColor(colorScheme.surfaceContainerHighest.toArgb())
            },
        )

        // Row 3: Quick actions
        if (mode == BottomPanelMode.AI) {
            QuickActions(
                isRunning = isAiRunning,
                currentFile = currentFile,
                hasSelection = hasSelection,
                onAction = onAction,
            )
        } else if (mode == BottomPanelMode.TERMINAL) {
            TerminalQuickActions(
                terminalViewModel = terminalViewModel,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun TerminalQuickActions(
    terminalViewModel: TerminalViewModel,
    onAction: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionChip(
            icon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp)) },
            label = "Paste",
            onClick = {
                terminalViewModel.terminalView?.mTermSession?.let { session ->
                    val clip = com.blankj.utilcode.util.ClipboardUtils.getText().toString()
                    if (clip.isNotBlank()) {
                        session.write(clip)
                    }
                }
            },
            color = colorScheme.secondaryContainer,
        )

        ActionChip(
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp)) },
            label = "Clear",
            onClick = {
                terminalViewModel.terminalView?.mTermSession?.write("clear\n")
            },
            color = colorScheme.secondaryContainer,
        )

        ActionChip(
            icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
            label = "New Session",
            onClick = {
                terminalViewModel.terminalView?.let { tv ->
                    val activity = tv.context as? android.app.Activity ?: return@let
                    val client = com.rk.terminal.TerminalBackEnd(terminalViewModel)
                    val service = terminalViewModel.sessionBinder?.getService()
                    val sessionBinder = terminalViewModel.sessionBinder ?: return@let
                    
                    // We don't have direct access to mainViewModel here easily if it's a private sub-component
                    // But in this context, UnifiedToolSheet provides viewModel
                    val activeTab = (tv.context as? MainActivity)?.viewModel?.currentTab as? EditorTab
                    val activeFile = activeTab?.file?.getAbsolutePath() ?: ""
                    val activeProject = activeTab?.projectRoot?.getAbsolutePath() ?: ""
                    
                    scope.launch(Dispatchers.IO) {
                        sessionBinder.createSession(
                            "main #${service?.sessionList?.size?.plus(1) ?: 1}",
                            client,
                            activity,
                            activeFile = activeFile,
                            activeProject = activeProject
                        )
                    }
                }
            },
            color = colorScheme.surfaceVariant,
        )

        ActionChip(
            icon = { Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(14.dp)) },
            label = "Kill Session",
            onClick = {
                terminalViewModel.terminalView?.mTermSession?.let { session ->
                    val binder = terminalViewModel.sessionBinder ?: return@let
                    val service = binder.getService() ?: return@let
                    val sessionId = service.sessionList.find { binder.getSession(it) == session }
                    if (sessionId != null) {
                        binder.terminateSession(sessionId)
                        val nextSession = service.sessionList.firstOrNull()
                        if (nextSession != null) {
                            val activity = terminalViewModel.terminalView?.context as? android.app.Activity
                            if (activity != null) {
                                scope.launch {
                                    com.rk.terminal.changeTerminalSession(nextSession, terminalViewModel, activity)
                                }
                            }
                        }
                    }
                }
            },
            color = colorScheme.errorContainer,
            labelColor = colorScheme.error,
        )
    }
}

@Composable
private fun StatusBar(
    mode: BottomPanelMode = BottomPanelMode.AI,
    isRunning: Boolean = false,
    cwd: String = "",
    agent: AiProvider.AgentInfo? = AiProvider.sessionManager?.currentAgent,
    availableAgents: List<AiProvider.AgentInfo> = emptyList(),
    showAgentMenu: Boolean = false,
    onToggleAgentMenu: () -> Unit = {},
    onSelectAgent: (AiProvider.AgentInfo) -> Unit = {},
    transcript: String = "",
    showTranscript: Boolean = false,
    onToggleTranscript: () -> Unit = {},
    onClearTranscript: () -> Unit = {},
    terminalViewModel: TerminalViewModel? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val statusColor = if (isRunning) Color(0xFF4CAF50) else Color(0xFFEF5350)
    val bridgeClients = AiProvider.ideBridge?.connectedClients() ?: 0
    val bridgeOnline = AiProvider.ideBridge?.isRunning() == true

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Agent pill
                Box {
                    Surface(
                        modifier = Modifier
                            .height(26.dp)
                            .clickable { if (mode == BottomPanelMode.AI) onToggleAgentMenu() },
                        shape = RoundedCornerShape(13.dp),
                        color = if (mode == BottomPanelMode.AI)
                            colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else
                            colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (mode == BottomPanelMode.AI) (agent?.displayName ?: "AI") else "Terminal",
                                color = if (mode == BottomPanelMode.AI) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                            if (mode == BottomPanelMode.AI) {
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Switch",
                                    modifier = Modifier.size(14.dp),
                                    tint = colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }

                    if (mode == BottomPanelMode.AI) {
                        DropdownMenu(
                            expanded = showAgentMenu,
                            onDismissRequest = { onToggleAgentMenu() },
                        ) {
                            availableAgents.forEachIndexed { index, a ->
                                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (a.cliBinaryName == "gemini") Icons.Outlined.Psychology else Icons.Outlined.AutoFixHigh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (a == agent) colorScheme.primary else colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                    Text(a.displayName, style = MaterialTheme.typography.bodyMedium)
                                                    Text(
                                                        "Switch to ${a.displayName.split("/").last()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                )
                                            }
                                            if (a == agent) {
                                                Spacer(Modifier.width(8.dp))
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = colorScheme.primary,
                                                )
                                            }
                                        }
                                    },
                                    onClick = { onSelectAgent(a) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // CWD chip
                Surface(
                    onClick = {
                        com.blankj.utilcode.util.ClipboardUtils.copyText("Path", cwd)
                        com.rk.utils.toast("Path copied to clipboard")
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Code,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = cwd.split("/").lastOrNull()?.takeIf { it.isNotBlank() } ?: "/",
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Bridge status dot
                if (bridgeOnline && mode == BottomPanelMode.AI) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (bridgeClients > 0) Color(0xFF4CAF50)
                                        else Color(0xFFFFC107)
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }

                Spacer(Modifier.weight(1f))

                // Transcript controls
                if (mode == BottomPanelMode.AI && transcript.isNotBlank()) {
                    FilledTonalIconButton(onClick = onToggleTranscript, modifier = Modifier.size(26.dp)) {
                        Icon(
                            if (showTranscript) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (showTranscript) "Hide transcript" else "Show transcript",
                            modifier = Modifier.size(16.dp),
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    FilledTonalIconButton(onClick = onClearTranscript, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Clear transcript",
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                    .heightIn(max = 140.dp)
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                val scrollState = rememberScrollState()
                LaunchedEffect(transcript) {
                    if (showTranscript) scrollState.animateScrollTo(scrollState.maxValue)
                }
                Text(
                    text = transcript,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .verticalScroll(scrollState),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colorScheme.onSurface,
                )
            }
        }
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isRunning) {
            Button(
                onClick = { onAction("/restart") },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start Agent", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            ActionChip(
                icon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Explain",
                onClick = { onAction(prompt("Explain the code")) },
                color = colorScheme.secondaryContainer,
            )
            ActionChip(
                icon = { Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Bugs",
                onClick = { onAction(prompt("Find bugs and issues")) },
                color = colorScheme.secondaryContainer,
            )
            ActionChip(
                icon = { Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Refactor",
                onClick = { onAction(prompt("Suggest improvements")) },
                color = colorScheme.secondaryContainer,
            )
            ActionChip(
                icon = { Icon(Icons.Outlined.Science, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Tests",
                onClick = { onAction(prompt("Add unit tests")) },
                color = colorScheme.secondaryContainer,
            )
            ActionChip(
                icon = { Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Docs",
                onClick = { onAction(prompt("Write documentation")) },
                color = colorScheme.secondaryContainer,
            )

            Spacer(Modifier.width(4.dp))
            VerticalDivider(modifier = Modifier.height(18.dp), color = colorScheme.outlineVariant)
            Spacer(Modifier.width(4.dp))

            ActionChip(
                icon = { Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Export",
                onClick = { onAction("/export") },
                color = colorScheme.surfaceVariant,
            )
            ActionChip(
                icon = { Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = "Stop",
                onClick = { onAction("/stop") },
                color = colorScheme.errorContainer,
                labelColor = colorScheme.error,
            )
        }
    }
}

@Composable
private fun ActionChip(
    icon: (@Composable () -> Unit)? = null,
    label: String,
    onClick: () -> Unit,
    color: Color,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = color,
        modifier = Modifier.height(30.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.invoke()
            if (icon != null) Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

private fun exportSession(viewModel: MainViewModel, cwd: String) {
    val context = com.rk.utils.application ?: return
    val agentName = AiProvider.sessionManager?.currentAgent?.displayName ?: "AI"
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
