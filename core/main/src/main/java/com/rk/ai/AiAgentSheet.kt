package com.rk.ai

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.activities.main.BottomPanelMode
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.activities.main.gitViewModel
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.filetree.drawerTabs
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.SheetTerminal
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
        viewModel.agentTranscript =
            listOf(viewModel.agentTranscript, text)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
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
                onAction = { logic.handleInput(it) },
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
                        SheetTerminal(session = aiSession, modifier = Modifier.fillMaxSize(), showKeys = false)
                    } else {
                        AgentEmptyState(
                            isRunning = isAiRunning,
                            agentName = currentAgent?.displayName ?: "AI",
                            onStart = { logic.startAgent(cwd.value, forceRestart = true) },
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
                        LaunchedEffect(viewModel.terminalCwd) {
                            if (viewModel.terminalCwd != null) {
                                viewModel.terminalCwd = null
                            }
                        }
                    }
                }
                BottomPanelMode.GIT -> {
                    if (gitVm != null) {
                        GitPanel(
                            gitViewModel = gitVm,
                            onRefresh = {
                                scope.launch {
                                    val root = gitVm.currentRoot.value?.absolutePath
                                    if (root != null) gitVm.syncChanges(root)
                                }
                            },
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Git not available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
