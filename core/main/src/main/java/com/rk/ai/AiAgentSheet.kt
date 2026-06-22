package com.rk.ai

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.PlayArrow
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
internal fun AgentEmptyState(
    isRunning: Boolean,
    agentName: String,
    onStart: () -> Unit,
    cwd: String = "",
    transcript: String = "",
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = colorScheme.primaryContainer.copy(alpha = 0.4f),
                tonalElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "$agentName",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isRunning) Color(0xFF4CAF50) else Color(0xFF9E9E9E)))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isRunning) "Active" else "Idle",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            if (cwd.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.widthIn(max = 200.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(12.dp), tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            cwd.split("/").lastOrNull()?.takeIf { it.isNotBlank() } ?: "/",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (transcript.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Previous session available",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onStart,
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start Agent", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
