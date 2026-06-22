package com.rk.ai

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.activities.main.BottomPanelMode
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.gitViewModel
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.filetree.drawerTabs
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.ToolSheetContainer
import com.rk.terminal.TerminalViewModel
import com.termux.terminal.TerminalSession
import java.io.File
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
    
    val aiSession = AiProvider.sessionManager?.sessionState?.value as? TerminalSession
    val isAiRunning = aiSession?.isRunning == true
    var showTranscript by remember { mutableStateOf(false) }
    val transcript = viewModel.agentTranscript

    fun appendLog(text: String) {
        val current = viewModel.agentTranscript
        viewModel.agentTranscript = if (current.isBlank()) text else "$current\n\n$text"
    }

    val logic = remember(activity, viewModel, scope) {
        AiSessionLogic(
            viewModel = viewModel,
            activity = activity,
            scope = scope,
            cwd = { cwd.value },
            appendLog = ::appendLog,
        )
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

    LaunchedEffect(Unit) {
        val pendingPrompt = logic.consumePendingPrompt()
        if (pendingPrompt != null) {
            logic.handleInput(pendingPrompt)
        } else if (AiProvider.sessionManager?.canReuseFor(cwd.value) != true) {
            logic.startAgent(cwd.value)
        }
    }

    LaunchedEffect(viewModel.agentPrompt, viewModel.showBottomPanel) {
        if (!viewModel.showBottomPanel) return@LaunchedEffect
        if (viewModel.bottomPanelMode == BottomPanelMode.AI) {
            logic.consumePendingPrompt()?.let { logic.handleInput(it) }
        }
    }

    val currentAgent = AiProvider.sessionManager?.currentAgent
    val currentTab = viewModel.currentTab as? EditorTab
    val editor = currentTab?.editorState?.editor?.get()
    val selectedText = editor?.getSelectedText().orEmpty()

    val gitVm = gitViewModel.get()
    LaunchedEffect(viewModel.bottomPanelMode, gitVm) {
        if (viewModel.bottomPanelMode == BottomPanelMode.GIT && gitVm?.currentRoot?.value == null) {
            val tab = currentDrawerTab
            val root = if (tab is FileTreeTab) {
                File(tab.root.getAbsolutePath())
            } else null
            if (root != null && org.eclipse.jgit.storage.file.FileRepositoryBuilder().findGitDir(root).gitDir != null) {
                gitVm?.loadRepository(root.absolutePath)
            }
        }
    }

    val vibecodingEngine = remember(activity) {
        activity?.let { act ->
            AiProvider.ideBridge?.setWorkspacePath(cwd.value)
            val factory = AiProvider.ideServiceFactory ?: return@let null
            VibeCodingEngine(context = act, ideService = factory.create(viewModel))
        }
    }

    LaunchedEffect(cwd.value) {
        AiProvider.ideBridge?.setWorkspacePath(cwd.value)
    }

    DisposableEffect(activity) {
        onDispose {
            vibecodingEngine?.dispose()
        }
    }

    // Engine lifecycle managed via remember + DisposableEffect above

    ToolSheetContainer(
        onDismissRequest = onDismissRequest,
        cwd = cwd.value,
        session = null,
        modifier = modifier,
        showTerminal = false,
        headerContent = {
            ToolSheetTabBar(
                selectedMode = viewModel.bottomPanelMode,
                onSelectTab = { viewModel.bottomPanelMode = it },
                terminalViewModel = terminalViewModel,
            )
        },
        controls = {
            ToolSheetControls(
                mode = viewModel.bottomPanelMode,
                onRestartAgent = { logic.startAgent(cwd.value, forceRestart = true) },
                onSyncFiles = {
                    scope.launch(Dispatchers.IO) {
                        val saved = saveDirtyEditors()
                        withContext(Dispatchers.Main) { appendLog("Synced $saved dirty editor file(s).") }
                    }
                },
                canUndo = editor?.canUndo() == true,
                onUndo = {
                    if (editor?.canUndo() == true) {
                        editor.undo()
                        currentTab?.editorState?.updateUndoRedo()
                    }
                },
                canRedo = editor?.canRedo() == true,
                onRedo = {
                    if (editor?.canRedo() == true) {
                        editor.redo()
                        currentTab?.editorState?.updateUndoRedo()
                    }
                },
                terminalViewModel = terminalViewModel,
            )
        },
        bottomBar = if (viewModel.bottomPanelMode == BottomPanelMode.VIBE_CODING) null else {
            {
                UnifiedCommandBar(
                    mode = viewModel.bottomPanelMode,
                    isAiRunning = isAiRunning,
                    cwd = cwd.value,
                    agent = currentAgent,
                    transcript = transcript,
                    showTranscript = showTranscript,
                    onToggleTranscript = { showTranscript = !showTranscript },
                    onClearTranscript = { viewModel.agentTranscript = "" },
                    onAction = { logic.handleInput(it) },
                    terminalViewModel = terminalViewModel,
                    hasSelection = selectedText.isNotBlank(),
                    selectedText = selectedText,
                    currentFile = cwd.value.split("/").lastOrNull() ?: ""
                )
            }
        },
    ) {
        when (viewModel.bottomPanelMode) {
            BottomPanelMode.AI -> AiPanel(
                aiSession = aiSession,
                isAiRunning = isAiRunning,
                agentName = currentAgent?.displayName ?: "AI",
                onStart = { logic.startAgent(cwd.value, forceRestart = true) },
                cwd = cwd.value,
                transcript = transcript,
                onClearTranscript = { viewModel.agentTranscript = "" },
                onToggleTranscript = { showTranscript = !showTranscript },
            )
            BottomPanelMode.VIBE_CODING -> VibeCodingPanelContent(engine = vibecodingEngine)
            BottomPanelMode.TERMINAL -> TerminalPanelContent(
                terminalViewModel = terminalViewModel,
                initialCwd = viewModel.terminalCwd,
                onCwdConsumed = { viewModel.terminalCwd = null },
            )
            BottomPanelMode.GIT -> GitPanelContent(
                gitViewModel = gitVm,
                onRefresh = {
                    scope.launch {
                        val root = gitVm?.currentRoot?.value?.absolutePath
                        if (root != null) gitVm?.syncChanges(root)
                    }
                },
            )
        }
    }
}

@Composable
internal fun AiSessionOverview(
    isRunning: Boolean,
    agentName: String,
    onStart: () -> Unit,
    cwd: String = "",
    transcript: String = "",
    onClearTranscript: () -> Unit = {},
    onToggleTranscript: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val bridgeClients = AiProvider.ideBridge?.connectedClients() ?: 0
    val bridgeTools = AiProvider.ideBridge?.availableTools() ?: 0
    val bridgeOnline = AiProvider.ideBridge?.isRunning() == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Session Overview",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Agent:",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = agentName,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                        )
                        Text(
                            text = if (isRunning) "Active" else "Idle",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Workspace:",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = cwd,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Bridge status:",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (bridgeOnline) "Online ($bridgeTools tools, $bridgeClients clients)" else "Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (bridgeOnline) colorScheme.primary else colorScheme.error
                        )
                    }

                    if (transcript.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Transcript:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${transcript.trim().split("\n").size} lines",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = colorScheme.outlineVariant.copy(alpha = 0.12f),
                thickness = 0.5.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (transcript.isNotBlank()) "Resume" else "Start Agent", style = MaterialTheme.typography.labelSmall)
                }

                if (transcript.isNotBlank()) {
                    OutlinedButton(
                        onClick = onToggleTranscript,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Show Log", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = onClearTranscript,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error),
                        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear Log", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
