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
import com.rk.ai.session.GeminiSessionManager
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.filetree.drawerTabs
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.GeminiCliSheet
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedGeminiSheet(
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
    val session = GeminiSessionManager.session
    val isRunning = session?.isRunning == true
    var showTranscript by remember { mutableStateOf(false) }
    val transcript = viewModel.geminiCliTranscript

    fun appendLog(text: String) {
        viewModel.geminiCliTranscript =
            listOf(viewModel.geminiCliTranscript, text)
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

    fun startGemini(workingDir: String = cwd.value, extraArgs: List<String> = emptyList(), forceRestart: Boolean = false) {
        val currentActivity = activity ?: return
        if (!forceRestart && extraArgs.isEmpty() && GeminiSessionManager.canReuseFor(workingDir)) {
            GeminiBridge.setWorkspacePath(workingDir)
            appendLog("Reusing Gemini CLI session: ${GeminiSessionManager.cwd}")
            return
        }
        GeminiSessionManager.stopSession()
        scope.launch(Dispatchers.Main) {
            val saved = withContext(Dispatchers.IO) { saveDirtyEditors() }
            GeminiSessionManager.startSession(currentActivity, viewModel, workingDir, extraArgs)
            if (saved > 0) appendLog("Synced $saved dirty editor file(s) before Gemini start.")
            appendLog("Gemini CLI running in sheet: $workingDir")
        }
    }

    fun sendToGemini(text: String) {
        if (text.isBlank()) return
        val runningSession = GeminiSessionManager.session
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
            startGemini(dir, listOf("--prompt-interactive", text))
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
            "/restart" -> startGemini(forceRestart = true)
            "/stop" -> {
                GeminiSessionManager.stopSession()
                appendLog("Gemini CLI stopped.")
            }
            else -> sendToGemini(input)
        }
    }

    fun consumePendingPrompt(): String? {
        val pending = viewModel.geminiPrompt.trim()
        if (pending.isBlank()) return null
        viewModel.geminiPrompt = ""
        return pending
    }

    LaunchedEffect(Unit) {
        val pendingPrompt = consumePendingPrompt()
        if (pendingPrompt != null) {
            handleInput(pendingPrompt)
        } else if (!GeminiSessionManager.canReuseFor(cwd.value)) {
            startGemini(cwd.value)
        }
    }

    LaunchedEffect(viewModel.geminiPrompt, viewModel.showGeminiSheet) {
        if (!viewModel.showGeminiSheet) return@LaunchedEffect
        consumePendingPrompt()?.let { handleInput(it) }
    }

    GeminiCliSheet(
        onDismissRequest = onDismissRequest,
        cwd = cwd.value,
        session = session,
        modifier = modifier,
        headerContent = {
            StatusBar(
                isRunning = isRunning,
                cwd = cwd.value,
                transcript = transcript,
                showTranscript = showTranscript,
                onToggleTranscript = { showTranscript = !showTranscript },
                onClearTranscript = {
                    viewModel.geminiCliTranscript = ""
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

            IconButton(onClick = { startGemini(cwd.value, forceRestart = true) }) {
                XedIcon(com.rk.icons.Icon.DrawableRes(drawables.restart), contentDescription = "Restart")
            }

            IconButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    val saved = saveDirtyEditors()
                    withContext(Dispatchers.Main) { appendLog("Synced $saved dirty editor file(s).") }
                }
            }) {
                XedIcon(com.rk.icons.Icon.DrawableRes(drawables.save), contentDescription = "Sync")
            }

            IconButton(onClick = {
                GeminiSessionManager.stopSession()
                appendLog("Gemini CLI stopped.")
            }, enabled = isRunning) {
                Icon(Icons.Outlined.Close, contentDescription = "Stop", tint = colorScheme.error.copy(alpha = 0.7f))
            }
        },
        bottomBar = {
            QuickActions(
                isRunning = isRunning,
                onAction = { handleInput(it) },
            )
        },
    )
}

@Composable
private fun StatusBar(
    isRunning: Boolean,
    cwd: String,
    transcript: String,
    showTranscript: Boolean,
    onToggleTranscript: () -> Unit,
    onClearTranscript: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dotColor = if (isRunning) Color(0xFF4CAF50) else Color(0xFFEF5350)

    Column(modifier = Modifier.fillMaxWidth()) {
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
                text = if (isRunning) "Running" else "Stopped",
                color = dotColor,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(12.dp))
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

@Composable
private fun QuickActions(
    isRunning: Boolean,
    onAction: (String) -> Unit,
) {
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
                label = { Text("Start Gemini", style = MaterialTheme.typography.labelSmall) },
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
                onClick = { onAction("/refresh") },
                label = { Text("Refresh", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction("Explain the current file") },
                label = { Text("Explain", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction("Find any bugs or issues in the current file") },
                label = { Text("Find Bugs", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction("Suggest improvements for the current file") },
                label = { Text("Refactor", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction("Add unit tests for the current file") },
                label = { Text("Add Tests", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
            AssistChip(
                onClick = { onAction("Write documentation comments for the current file") },
                label = { Text("Document", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}
