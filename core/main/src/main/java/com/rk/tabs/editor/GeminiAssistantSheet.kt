package com.rk.tabs.editor

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rk.ai.GeminiBridge
import com.termux.terminal.TerminalSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorTab.GeminiAssistantSheet() {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val colorScheme = MaterialTheme.colorScheme
    var session by remember { mutableStateOf<TerminalSession?>(null) }

    fun currentProjectDir(): String =
        projectRoot?.getAbsolutePath()
            ?: File(file.getAbsolutePath()).parent
            ?: file.getAbsolutePath()

    fun currentEditor() = editorState.editor.get()

    fun selectedOrFileText(): String {
        val editor = currentEditor() ?: return editorState.content?.toString().orEmpty()
        return if (editor.isTextSelected) {
            val start = minOf(editor.cursorRange.startIndex, editor.cursorRange.endIndex)
            val end = maxOf(editor.cursorRange.startIndex, editor.cursorRange.endIndex)
            editor.text.substring(start, end)
        } else {
            editor.text.toString()
        }
    }

    fun appendLog(text: String) {
        editorState.geminiCliTranscript =
            listOf(editorState.geminiCliTranscript, text)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
    }

    fun recentConversationContext(): String =
        editorState.geminiCliTranscript
            .takeLast(12 * 1024)
            .takeIf { it.isNotBlank() }
            ?.let { "Recent Xed Gemini notes:\n```\n$it\n```" }
            .orEmpty()

    fun geminiPrompt(request: String): String =
        buildGeminiSheetPrompt(
            request = request,
            projectDir = currentProjectDir(),
            filePath = file.getAbsolutePath(),
            contextText = selectedOrFileText(),
            hasSelection = currentEditor()?.isTextSelected == true,
            recentContext = recentConversationContext(),
        )

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
        session?.finishIfRunning()
        session = null
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
                session = newSession
                appendLog("Gemini CLI running in sheet: ${currentProjectDir()}")
            }
        }
    }

    fun sendToGemini(text: String) {
        if (text.isBlank()) return
        val runningSession = session
        val prompt = geminiPrompt(text)
        if (runningSession?.isRunning == true && runningSession.emulator != null) {
            scope.launch(Dispatchers.IO) {
                val saved = saveDirtyEditors()
                withContext(Dispatchers.Main) {
                    if (saved > 0) appendLog("Synced $saved dirty editor file(s).")
                    runningSession.write("$prompt\r")
                }
            }
        } else {
            startGemini(listOf("--prompt-interactive", prompt))
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
                session?.finishIfRunning()
                session = null
                appendLog("Gemini CLI stopped.")
            }
            else -> sendToGemini(input)
        }
        editorState.geminiPrompt = ""
    }

    LaunchedEffect(Unit) {
        if (session == null) startGemini()
    }

    LaunchedEffect(session) {
        while (session?.isRunning == true) {
            delay(1500)
            refreshCleanEditors()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            session?.finishIfRunning()
            session = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = { editorState.showGeminiAssistant = false },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceContainerHighest, RoundedCornerShape(18.dp))
                        .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                        .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✦ Gemini CLI", color = colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text("Sheet terminal + Xed bridge", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "cwd ${currentProjectDir()}",
                        color = colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    GeminiSheetTerminal(session = session, modifier = Modifier.fillMaxWidth())

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { startGemini() }) { Text("Restart") }
                        TextButton(onClick = { startGemini(listOf("--prompt-interactive", "/auth")) }) { Text("Auth") }
                        TextButton(onClick = { session?.write("\u0003") }) { Text("Ctrl+C") }
                        TextButton(onClick = { session?.write("\r") }) { Text("Enter") }
                        TextButton(onClick = { session?.write("\u001B") }) { Text("Esc") }
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
                            session?.finishIfRunning()
                            session = null
                            appendLog("Gemini CLI stopped.")
                        }) { Text("Stop") }
                        TextButton(onClick = {
                            editorState.geminiCliTranscript = ""
                            editorState.geminiOutput = ""
                            editorState.geminiRawLog = ""
                        }) { Text("Clear") }
                    }

                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "> ",
                            color = colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        OutlinedTextField(
                            value = editorState.geminiPrompt,
                            onValueChange = { editorState.geminiPrompt = it },
                            modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                            label = { Text("Send to Gemini CLI in this terminal") },
                            minLines = 1,
                            maxLines = 4,
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = editorState.geminiPrompt.isNotBlank(),
                            onClick = { handleSend() },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Send")
                        }
                    }
                }
            }
        }
    }
}
