package com.rk.tabs.editor

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
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
fun EditorTab.GeminiAssistantSheet() {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    fun terminalHomeDir(): String =
        if (Settings.sandbox) "/home" else sandboxHomeDir().absolutePath

    fun currentProjectDir(): String {
        projectRoot?.getAbsolutePath()?.takeIf { it.isNotBlank() }?.let { return it }

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

    fun startGemini(extraArgs: List<String> = emptyList()) {
        val currentActivity = activity ?: return
        editorState.geminiCliSession?.finishIfRunning()
        editorState.geminiCliSession = null
        scope.launch(Dispatchers.IO) {
            val saved = saveDirtyEditors()
            val bridge = GeminiBridge.ensureStarted(viewModel, currentProjectDir())
            withContext(Dispatchers.Main) {
                val newSession = createGeminiSheetSession(
                    activity = currentActivity,
                    bridge = bridge,
                    workingDir = currentProjectDir(),
                    extraArgs = extraArgs,
                )
                if (saved > 0) appendLog("Synced $saved dirty editor file(s) before Gemini start.")
                editorState.geminiCliSession = newSession
                editorState.geminiCliSessionCwd = currentProjectDir()
                appendLog("Gemini CLI running in sheet: ${currentProjectDir()}")
            }
        }
    }

    fun sendToGemini(text: String) {
        if (text.isBlank()) return
        val runningSession = editorState.geminiCliSession
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
            "/restart" -> startGemini()
            "/stop" -> {
                editorState.geminiCliSession?.finishIfRunning()
                editorState.geminiCliSession = null
                appendLog("Gemini CLI stopped.")
            }
            else -> sendToGemini(input)
        }
        editorState.geminiPrompt = ""
    }

    LaunchedEffect(Unit) {
        val existing = editorState.geminiCliSession
        if (existing == null || !existing.isRunning || editorState.geminiCliSessionCwd != currentProjectDir()) {
            startGemini()
        }
    }

    LaunchedEffect(editorState.geminiCliSession) {
        while (editorState.geminiCliSession?.isRunning == true) {
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
        session = editorState.geminiCliSession,
        prompt = editorState.geminiPrompt,
        onPromptChange = { editorState.geminiPrompt = it },
        onSend = { handleSend() },
        controls = {
            TextButton(onClick = { startGemini() }) { Text("Restart") }
            TextButton(onClick = { startGemini(listOf("--prompt-interactive", "/auth")) }) { Text("Auth") }
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
                editorState.geminiCliSession?.finishIfRunning()
                editorState.geminiCliSession = null
                appendLog("Gemini CLI stopped.")
            }) { Text("Stop") }
        },
    )
}
