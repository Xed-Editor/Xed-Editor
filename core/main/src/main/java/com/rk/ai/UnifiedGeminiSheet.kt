package com.rk.ai

import android.app.Activity
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rk.activities.main.MainViewModel
import com.rk.ai.session.GeminiSessionManager
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.filetree.drawerTabs
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.GeminiCliSheet
import com.rk.tabs.editor.createGeminiSheetSession
import com.rk.xededitor.BuildConfig
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
    fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d("UnifiedGeminiSheet", msg)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity

    fun terminalHomeDir(): String =
        if (Settings.sandbox) "/home" else sandboxHomeDir().absolutePath

    fun currentProjectDir(): String {
        // 1. Try active editor tab
        val activeTab = viewModel.currentTab as? EditorTab
        activeTab?.projectRoot?.getAbsolutePath()?.takeIf { it.isNotBlank() && it.startsWith("/") }?.let { return it }
        
        // 2. Try drawer project
        val projectDir = ((currentDrawerTab as? FileTreeTab)?.root as? FileWrapper)?.getAbsolutePath()
            ?: drawerTabs.filterIsInstance<FileTreeTab>().mapNotNull { it.root as? FileWrapper }.firstOrNull()?.getAbsolutePath()
        
        if (projectDir != null && projectDir.isNotBlank()) return projectDir

        // 3. Fallback to active file parent or home
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
            scope.launch(Dispatchers.IO) { GeminiBridge.ensureStarted(viewModel, workingDir) }
            appendLog("Reusing Gemini CLI session: ${GeminiSessionManager.cwd}")
            d("reuse session cwd=${GeminiSessionManager.cwd} requested=$workingDir")
            return
        }
        d("startGemini forceRestart=$forceRestart cwd=$workingDir extraArgs=${extraArgs.joinToString(" ")}")
        GeminiSessionManager.stopSession()
        scope.launch(Dispatchers.Main) {
            val saved = withContext(Dispatchers.IO) { saveDirtyEditors() }
            GeminiSessionManager.startSession(currentActivity, viewModel, workingDir, extraArgs)
            if (saved > 0) appendLog("Synced $saved dirty editor file(s) before Gemini start.")
            appendLog("Gemini CLI running in sheet: $workingDir")
            d("session started cwd=$workingDir")
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

    fun handleSend() {
        val input = viewModel.geminiPrompt.trim()
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
                d("session stopped from command")
            }
            else -> sendToGemini(input)
        }
        viewModel.geminiPrompt = ""
    }

    LaunchedEffect(Unit) {
        if (!GeminiSessionManager.canReuseFor(defaultCwd)) {
            startGemini(defaultCwd)
        }
    }

    LaunchedEffect(GeminiSessionManager.session) {
        while (true) {
            val session = GeminiSessionManager.session
            if (session != null && session.isRunning) {
                refreshCleanEditors()
            }
            delay(3000)
        }
    }
    
    // Handle external prompt requests (e.g. from editor selection)
    LaunchedEffect(viewModel.geminiPrompt) {
        if (viewModel.geminiPrompt.isNotBlank() && viewModel.showGeminiSheet) {
            handleSend()
        }
    }

    GeminiCliSheet(
        onDismissRequest = onDismissRequest,
        cwd = defaultCwd,
        session = GeminiSessionManager.session,
        modifier = modifier,
        controls = {
            IconButton(onClick = { startGemini(defaultCwd, forceRestart = true) }) { 
                Icon(Icons.Outlined.Refresh, contentDescription = "Restart", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { startGemini(defaultCwd, listOf("--prompt-interactive", "/auth"), forceRestart = true) }) { 
                Icon(Icons.Outlined.Lock, contentDescription = "Auth", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    val saved = saveDirtyEditors()
                    withContext(Dispatchers.Main) { appendLog("Synced $saved dirty editor file(s).") }
                }
            }) { 
                Icon(Icons.Outlined.Refresh, contentDescription = "Sync", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                refreshCleanEditors()
                appendLog("Refreshed clean editor tabs.")
            }) { 
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                GeminiSessionManager.stopSession()
                appendLog("Gemini CLI stopped.")
            }) { 
                Icon(Icons.Outlined.Close, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        },
    )
}
