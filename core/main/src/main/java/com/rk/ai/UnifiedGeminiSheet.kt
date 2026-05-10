package com.rk.ai

import android.app.Activity
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rk.activities.main.MainViewModel
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.filetree.drawerTabs
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.tabs.editor.GeminiCliSheet
import com.rk.tabs.editor.GeminiSheetSessionStore
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
        if (!forceRestart && extraArgs.isEmpty() && GeminiSheetSessionStore.canReuseFor(workingDir)) {
            scope.launch(Dispatchers.IO) { GeminiBridge.ensureStarted(viewModel, workingDir) }
            appendLog("Reusing Gemini CLI session: ${GeminiSheetSessionStore.cwd}")
            d("reuse session cwd=${GeminiSheetSessionStore.cwd} requested=$workingDir")
            return
        }
        d("startGemini forceRestart=$forceRestart cwd=$workingDir extraArgs=${extraArgs.joinToString(" ")}")
        GeminiSheetSessionStore.stop()
        scope.launch(Dispatchers.IO) {
            val saved = saveDirtyEditors()
            val bridge = GeminiBridge.ensureStarted(viewModel, workingDir)
            withContext(Dispatchers.Main) {
                val newSession = createGeminiSheetSession(
                    activity = currentActivity,
                    bridge = bridge,
                    workingDir = workingDir,
                    extraArgs = extraArgs,
                )
                if (saved > 0) appendLog("Synced $saved dirty editor file(s) before Gemini start.")
                GeminiSheetSessionStore.session = newSession
                GeminiSheetSessionStore.cwd = workingDir
                appendLog("Gemini CLI running in sheet: $workingDir")
                d("session started cwd=$workingDir")
            }
        }
    }

    fun sendToGemini(text: String) {
        if (text.isBlank()) return
        val runningSession = GeminiSheetSessionStore.session
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
                GeminiSheetSessionStore.stop()
                appendLog("Gemini CLI stopped.")
                d("session stopped from command")
            }
            else -> sendToGemini(input)
        }
        viewModel.geminiPrompt = ""
    }

    LaunchedEffect(Unit) {
        if (!GeminiSheetSessionStore.canReuseFor(defaultCwd)) {
            startGemini(defaultCwd)
        }
    }

    LaunchedEffect(GeminiSheetSessionStore.session) {
        while (GeminiSheetSessionStore.session?.isRunning == true) {
            delay(1500)
            refreshCleanEditors()
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
        session = GeminiSheetSessionStore.session,
        modifier = modifier,
        controls = {
            TextButton(onClick = { startGemini(defaultCwd, forceRestart = true) }) { Text("Restart") }
            TextButton(onClick = { startGemini(defaultCwd, listOf("--prompt-interactive", "/auth"), forceRestart = true) }) { Text("Auth") }
            TextButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    val saved = saveDirtyEditors()
                    withContext(Dispatchers.Main) { appendLog("Synced $saved dirty editor file(s).") }
                }
            }) { Text("Sync") }
            TextButton(onClick = {
                refreshCleanEditors()
                appendLog("Refreshed clean editor tabs.")
            }) { Text("Refresh") }
            TextButton(onClick = {
                GeminiSheetSessionStore.stop()
                appendLog("Gemini CLI stopped.")
            }) { Text("Stop") }
        },
    )
}
