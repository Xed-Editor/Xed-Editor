package com.rk.ai

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
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

    val defaultCwd = remember(viewModel.currentTab, currentDrawerTab) { currentProjectDir() }

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

    fun startGemini(workingDir: String = defaultCwd, extraArgs: List<String> = emptyList(), forceRestart: Boolean = false) {
        val currentActivity = activity ?: return
        if (!forceRestart && extraArgs.isEmpty() && GeminiSessionManager.canReuseFor(workingDir)) {
            scope.launch(Dispatchers.IO) { 
                GeminiBridge.ensureStarted(viewModel)
                GeminiBridge.setWorkspacePath(workingDir)
            }
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
            startGemini(defaultCwd, listOf("--prompt-interactive", text))
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
        } else if (!GeminiSessionManager.canReuseFor(defaultCwd)) {
            startGemini(defaultCwd)
        }
    }

    LaunchedEffect(viewModel.geminiPrompt, viewModel.showGeminiSheet) {
        if (!viewModel.showGeminiSheet) return@LaunchedEffect
        consumePendingPrompt()?.let { handleInput(it) }
    }

    GeminiCliSheet(
        onDismissRequest = onDismissRequest,
        cwd = defaultCwd,
        session = GeminiSessionManager.session,
        modifier = modifier,
        controls = {
            val currentTab = viewModel.currentTab as? EditorTab
            val editor = currentTab?.editorState?.editor?.get()
            val running = GeminiSessionManager.session?.isRunning == true

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

            IconButton(onClick = { startGemini(defaultCwd, forceRestart = true) }) {
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
            }, enabled = running) {
                Icon(Icons.Outlined.Close, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        },
    )
}
