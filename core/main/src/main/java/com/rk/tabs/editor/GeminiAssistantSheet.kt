package com.rk.tabs.editor

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.rk.ai.GeminiBridge
import com.rk.file.sandboxHomeDir
import com.rk.settings.Settings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTab.GeminiAssistantSheet(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    fun terminalHomeDir(): String =
        if (Settings.sandbox) "/home" else sandboxHomeDir().absolutePath

    fun currentProjectDir(): String {
        projectRoot?.getAbsolutePath()?.takeIf { it.isNotBlank() && it.startsWith("/") }?.let { return it }

        val path = file.getAbsolutePath().takeIf { it.startsWith("/") } ?: return terminalHomeDir()
        val localFile = File(path)
        if (localFile.isDirectory) return localFile.absolutePath
        return localFile.parent?.takeIf { it.isNotBlank() } ?: terminalHomeDir()
    }

    fun appendLog(text: String) {
        editorState.geminiCliTranscript =
            listOf(editorState.geminiCliTranscript, text)
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

    fun startGemini(extraArgs: List<String> = emptyList(), forceRestart: Boolean = false) {
        val currentActivity = activity ?: return
        val workingDir = currentProjectDir()
        if (!forceRestart && extraArgs.isEmpty() && GeminiSheetSessionStore.canReuseFor(workingDir)) {
            appendLog("Reusing Gemini CLI session: ${GeminiSheetSessionStore.cwd}")
            return
        }
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
            startGemini(listOf("--prompt-interactive", text))
        }
    }

    fun handleSend() {
        val input = editorState.geminiPrompt.trim()
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
            }
            else -> sendToGemini(input)
        }
        editorState.geminiPrompt = ""
    }

    LaunchedEffect(Unit) {
        if (!GeminiSheetSessionStore.canReuseFor(currentProjectDir())) {
            startGemini()
        }
    }

    LaunchedEffect(GeminiSheetSessionStore.session) {
        while (GeminiSheetSessionStore.session?.isRunning == true) {
            delay(1500)
            refreshCleanEditors()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Keep the embedded Gemini terminal session alive when the sheet is dismissed.
        }
    }

    GeminiCliSheet(
        onDismissRequest = { editorState.showGeminiAssistant = false },
        cwd = currentProjectDir(),
        session = GeminiSheetSessionStore.session,
        modifier = modifier,
        controls = {
            TextButton(onClick = { startGemini(forceRestart = true) }) { Text("Restart") }
            TextButton(onClick = { startGemini(listOf("--prompt-interactive", "/auth"), forceRestart = true) }) { Text("Auth") }
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
