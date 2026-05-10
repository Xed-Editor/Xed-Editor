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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.ClipboardUtils
import com.rk.ai.GeminiBridge
import com.rk.ai.GeminiCli
import com.rk.exec.ShellUtils
import com.rk.exec.TerminalCommand
import com.rk.exec.launchTerminal
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class GeminiActionMode {
    Ask,
    Apply,
    Insert,
    Agent,
}

private data class GeminiPendingPatch(
    val title: String,
    val oldText: String,
    val newText: String,
    val apply: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorTab.GeminiAssistantSheet() {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    var pendingPatch by remember { mutableStateOf<GeminiPendingPatch?>(null) }

    fun currentEditor() = editorState.editor.get()

    fun selectedOrFileText(): String {
        val editor = currentEditor() ?: return editorState.content?.toString().orEmpty()
        return if (editor.isTextSelected) {
            editor.text.substring(editor.cursorRange.startIndex, editor.cursorRange.endIndex)
        } else {
            editor.text.toString()
        }
    }

    fun currentProjectDir(): String = projectRoot?.getAbsolutePath() ?: file.getAbsolutePath()

    fun appendGeminiCli(text: String) {
        editorState.geminiCliTranscript =
            listOf(editorState.geminiCliTranscript, text)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
    }

    fun commandPreview(prompt: String, mode: GeminiActionMode): String {
        val flag = if (mode == GeminiActionMode.Agent) "--yolo" else "-p"
        return "gemini $flag \"${prompt.lineSequence().firstOrNull().orEmpty().take(72)}${if (prompt.length > 72) "…" else ""}\""
    }

    fun geminiHelpText(): String =
        """
        Gemini CLI commands supported in this Xed sheet:

        /help, /?              Show this help
        /clear                 Clear visible sheet history
        /copy                  Copy last Gemini output/log
        /about                 Show Xed Gemini backend status
        /tools [desc]          Show available tool/back-end summary
        /ide status            Show IDE bridge status
        /directory show        Show active workspace directory
        /memory list           List GEMINI.md files in project parents
        /memory show           Show merged GEMINI.md memory
        /memory add <text>     Append text to project GEMINI.md
        /docs, /settings,
        /theme, /auth, ...     Use the CLI button for full interactive Gemini CLI dialogs

        @path/file             Works in prompts through Gemini CLI file tools
        !                      Toggle shell mode
        !command               Run shell command in project cwd

        Smart Xed actions: Ask, Apply, Insert, Agent.
        """
            .trimIndent()

    fun memoryFiles(): List<File> {
        val files = mutableListOf<File>()
        var dir: File? = File(currentProjectDir())
        while (dir != null) {
            val geminiMd = File(dir, "GEMINI.md")
            if (geminiMd.exists()) files.add(geminiMd)
            dir = dir.parentFile
        }
        val homeMemory = File(System.getProperty("user.home").orEmpty(), ".gemini/GEMINI.md")
        if (homeMemory.exists()) files.add(homeMemory)
        return files.distinctBy { it.absolutePath }
    }

    fun appendMemory(text: String) {
        val target = File(currentProjectDir(), "GEMINI.md")
        target.parentFile?.mkdirs()
        target.appendText("\n\n$text\n")
        appendGeminiCli("✔ Added memory to ${target.absolutePath}")
    }

    fun runShellCommand(command: String) {
        if (command.isBlank() || editorState.geminiRunning) return
        editorState.geminiRunning = true
        appendGeminiCli("${currentProjectDir()} ${'$'} $command")
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                ShellUtils.runUbuntu(
                    currentProjectDir(),
                    "/bin/bash",
                    "-lc",
                    command,
                    timeoutSeconds = 120,
                )
            }
            withContext(Dispatchers.Main) {
                editorState.geminiRunning = false
                result
                    .onSuccess { shellResult ->
                        val text =
                            buildString {
                                if (shellResult.output.isNotBlank()) appendLine(shellResult.output)
                                if (shellResult.error.isNotBlank()) appendLine(shellResult.error)
                                append("exit ${shellResult.exitCode}")
                                if (shellResult.timedOut) append(" (timed out)")
                            }.trim()
                        editorState.geminiOutput = text
                        editorState.geminiRawLog = text
                        appendGeminiCli(text)
                    }
                    .onFailure { throwable ->
                        val text = throwable.message ?: throwable.toString()
                        editorState.geminiOutput = text
                        editorState.geminiRawLog = throwable.stackTraceToString()
                        appendGeminiCli("✖ $text")
                    }
            }
        }
    }

    fun runGemini(prompt: String, mode: GeminiActionMode, applyResult: ((String) -> Unit)? = null) {
        if (prompt.isBlank() || editorState.geminiRunning) return

        editorState.geminiRunning = true
        editorState.geminiOutput = ""
        editorState.geminiOutput = "Starting Gemini ${mode.name.lowercase()} for ${file.getName()}..."
        appendGeminiCli(
            """
            ${currentProjectDir()} ${'$'} ${commandPreview(prompt, mode)}
            ⠋ Starting Gemini...
            """
                .trimIndent(),
        )

        scope.launch(Dispatchers.IO) {
            val workingDir = GeminiCli.workingDirFor(file, projectRoot)
            val bridge = GeminiBridge.ensureStarted(viewModel, workingDir)
            val bridgedPrompt =
                """
                Xed-Editor GUI bridge is available for this session.
                - Context endpoint: ${bridge.url}/context?token=${bridge.token}
                - Refresh editors endpoint: ${bridge.url}/refresh?token=${bridge.token}
                Use the context endpoint when you need current open files, active Sora editor file, cursor, or selected text.
                After editing files, call the refresh endpoint if possible so the Android GUI updates.

                $prompt
                """
                    .trimIndent()
            val result =
                runCatching {
                    if (mode == GeminiActionMode.Agent) {
                        GeminiCli.agent(prompt = bridgedPrompt, workingDir = workingDir, projectDir = workingDir, ideBridge = bridge)
                    } else {
                        GeminiCli.prompt(prompt = bridgedPrompt, workingDir = workingDir, projectDir = workingDir, ideBridge = bridge)
                    }
                }

            withContext(Dispatchers.Main) {
                editorState.geminiRunning = false
                result
                    .onSuccess { shellResult ->
                        val rawOutput = shellResult.output.ifBlank { shellResult.error }
                        val output = GeminiCli.cleanOutput(rawOutput).ifBlank { rawOutput.trim() }
                        val header =
                            if (shellResult.exitCode == 0) {
                                "Gemini finished successfully."
                            } else {
                                "Gemini failed with exit code ${shellResult.exitCode}."
                            }
                        editorState.geminiRawLog =
                            """
                            $header

                            STDOUT:
                            ${shellResult.output}

                            STDERR:
                            ${shellResult.error}
                            """
                                .trimIndent()
                        editorState.geminiOutput = "$header\n\n$output".trim()
                        appendGeminiCli(
                            """
                            ${if (shellResult.exitCode == 0) "✔" else "✖"} $header

                            $output
                            """
                                .trimIndent(),
                        )
                        if (shellResult.exitCode == 0 && applyResult != null) {
                            applyResult(GeminiCli.stripCodeFences(output))
                        }
                    }
                    .onFailure { throwable ->
                        editorState.geminiRawLog = throwable.stackTraceToString()
                        editorState.geminiOutput = throwable.message ?: throwable.toString()
                        appendGeminiCli("✖ ${throwable.message ?: throwable.toString()}")
                    }
            }
        }
    }

    fun openFullCli() {
        val currentActivity = activity ?: return
        val workingDir = currentProjectDir()
        val bridge = GeminiBridge.ensureStarted(viewModel, workingDir)
        launchTerminal(
            currentActivity,
            TerminalCommand(
                exe = "/bin/bash",
                args =
                    arrayOf(
                        localBinDir().child("gemini-cli").absolutePath,
                        "--skip-trust",
                        "--include-directories",
                        workingDir,
                    ),
                id = "gemini-cli-project",
                terminatePreviousSession = false,
                workingDir = workingDir,
                env =
                    arrayOf(
                        "GEMINI_CLI_IDE_SERVER_PORT=${bridge.port}",
                        "GEMINI_CLI_IDE_AUTH_TOKEN=${bridge.token}",
                        "GEMINI_CLI_IDE_WORKSPACE_PATH=${bridge.workspacePath}",
                    ),
            ),
        )
    }

    fun askCurrentPrompt() {
        val editor = currentEditor()
        val contextText = selectedOrFileText()
        runGemini(
            prompt =
                """
                You are an AI coding assistant inside Xed-Editor.
                User request: ${editorState.geminiPrompt}

                Project root: ${currentProjectDir()}
                File: ${file.getAbsolutePath()}
                Current ${if (editor?.isTextSelected == true) "selection" else "file"}:
                ```
                $contextText
                ```

                Answer concisely. Do not edit files.
                """
                    .trimIndent(),
            mode = GeminiActionMode.Ask,
        )
    }

    fun handleGeminiSubmit() {
        val input = editorState.geminiPrompt.trim()
        if (input.isBlank()) return

        when {
            input == "/clear" -> {
                editorState.geminiOutput = ""
                editorState.geminiRawLog = ""
                editorState.geminiCliTranscript = ""
                editorState.geminiPrompt = ""
            }

            input == "/copy" -> {
                ClipboardUtils.copyText("Gemini log", editorState.geminiRawLog.ifBlank { editorState.geminiOutput })
                toast(strings.copied)
                appendGeminiCli("✔ Copied last Gemini output")
                editorState.geminiPrompt = ""
            }

            input == "/help" || input == "/?" -> {
                appendGeminiCli(geminiHelpText())
                editorState.geminiOutput = geminiHelpText()
                editorState.geminiPrompt = ""
            }

            input == "/about" -> {
                val bridge = GeminiBridge.ensureStarted(viewModel, currentProjectDir())
                val text =
                    """
                    Gemini CLI for Xed-Editor
                    Backend: @google/gemini-cli via Termux/Ubuntu wrapper
                    IDE bridge: ${bridge.url}
                    Workspace: ${bridge.workspacePath}
                    Current file: ${file.getAbsolutePath()}
                    """
                        .trimIndent()
                appendGeminiCli(text)
                editorState.geminiOutput = text
                editorState.geminiPrompt = ""
            }

            input.startsWith("/tools") -> {
                val text =
                    """
                    Available backends/tools:

                    Gemini CLI tools: file read/write, grep/glob, shell execution, web/MCP tools when configured in Gemini CLI.
                    Xed IDE bridge tools:
                    - readFile: reads open editor/file content
                    - listFiles: lists project directories
                    - openDiff: writes proposed file content and refreshes Xed
                    - closeDiff: returns final file content

                    For Gemini's exact live tool registry, open CLI and run /tools${if (input.contains("desc")) " desc" else ""}.
                    """
                        .trimIndent()
                appendGeminiCli(text)
                editorState.geminiOutput = text
                editorState.geminiPrompt = ""
            }

            input == "/ide status" || input == "/ide" -> {
                val bridge = GeminiBridge.ensureStarted(viewModel, currentProjectDir())
                val text =
                    """
                    IDE integration enabled
                    Server: ${bridge.url}
                    Workspace: ${bridge.workspacePath}
                    Open editor tabs: ${viewModel.tabs.filterIsInstance<EditorTab>().size}
                    """
                        .trimIndent()
                appendGeminiCli(text)
                editorState.geminiOutput = text
                editorState.geminiPrompt = ""
            }

            input == "/directory show" || input == "/dir show" -> {
                val text = currentProjectDir()
                appendGeminiCli(text)
                editorState.geminiOutput = text
                editorState.geminiPrompt = ""
            }

            input == "/memory list" -> {
                val text = memoryFiles().joinToString("\n") { it.absolutePath }.ifBlank { "No GEMINI.md files found." }
                appendGeminiCli(text)
                editorState.geminiOutput = text
                editorState.geminiPrompt = ""
            }

            input == "/memory show" -> {
                val text =
                    memoryFiles().joinToString("\n\n") { memory ->
                        "===== ${memory.absolutePath} =====\n${runCatching { memory.readText() }.getOrDefault("")}"
                    }.ifBlank { "No GEMINI.md memory loaded." }
                appendGeminiCli(text)
                editorState.geminiOutput = text
                editorState.geminiPrompt = ""
            }

            input.startsWith("/memory add ") -> {
                appendMemory(input.removePrefix("/memory add ").trim())
                editorState.geminiPrompt = ""
            }

            input == "!" -> {
                editorState.geminiShellMode = !editorState.geminiShellMode
                appendGeminiCli(if (editorState.geminiShellMode) "Shell mode enabled. Type commands without !." else "Shell mode disabled.")
                editorState.geminiPrompt = ""
            }

            input.startsWith("!") -> {
                runShellCommand(input.removePrefix("!"))
                editorState.geminiPrompt = ""
            }

            editorState.geminiShellMode -> {
                runShellCommand(input)
                editorState.geminiPrompt = ""
            }

            input.startsWith("/") && listOf("/explain", "/bugs", "/refactor", "/tests").none { input.startsWith(it) } -> {
                appendGeminiCli("This command needs Gemini's interactive TUI. Tap CLI, then run: $input")
            }

            else -> {
                askCurrentPrompt()
                editorState.geminiPrompt = ""
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { editorState.showGeminiAssistant = false }) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1117), RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(18.dp))
                        .padding(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "✦ Gemini CLI", color = Color(0xFFE6EDF3), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Xed bridge", color = Color(0xFF7D8590), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "cwd ${currentProjectDir()}",
                        color = Color(0xFF7D8590),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text =
                            editorState.geminiCliTranscript.ifBlank {
                                """
                                Welcome to Gemini CLI
                                Type a prompt below, or use /explain /bugs /refactor /tests.
                                Use Agent for multi-file edits, CLI for the real interactive terminal.
                                """
                                    .trimIndent()
                            },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp).verticalScroll(rememberScrollState()),
                        color = Color(0xFFE6EDF3),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = if (editorState.geminiShellMode) "! " else "> ",
                            color = if (editorState.geminiShellMode) Color(0xFFF0883E) else Color(0xFF2F81F7),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        OutlinedTextField(
                            value = editorState.geminiPrompt,
                            onValueChange = { editorState.geminiPrompt = it },
                            modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                            label = { Text(if (editorState.geminiShellMode) "Shell command" else "Type your message or @path/to/file") },
                            minLines = 1,
                            maxLines = 4,
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank(),
                            onClick = { handleGeminiSubmit() },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Send")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Using: ${memoryFiles().size} GEMINI.md files | Xed IDE bridge | ${if (editorState.geminiShellMode) "shell mode" else "auto"}",
                            color = Color(0xFF7D8590),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "no sandbox",
                            color = Color(0xFFFF7B72),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { editorState.geminiPrompt = "/explain Explain this code and point out important behavior." }) {
                    Text("Explain")
                }
                TextButton(onClick = { editorState.geminiPrompt = "/bugs Find bugs and suggest a safe fix." }) {
                    Text("Find bugs")
                }
                TextButton(
                    onClick = {
                        editorState.geminiPrompt = "/refactor Refactor this code to be cleaner without changing behavior."
                    }
                ) {
                    Text("Refactor")
                }
                TextButton(onClick = { editorState.geminiPrompt = "/tests Generate or improve tests for this code." }) {
                    Text("Tests")
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank(),
                    onClick = { handleGeminiSubmit() },
                ) {
                    Text(strings.ask.getString())
                }

                Button(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank() && currentEditor() != null,
                    onClick = {
                        val editor = currentEditor() ?: return@Button
                        val hasSelection = editor.isTextSelected
                        val contextText = selectedOrFileText()
                        val start = if (hasSelection) editor.cursorRange.startIndex else 0
                        val end = if (hasSelection) editor.cursorRange.endIndex else editor.text.toString().length
                        runGemini(
                            prompt =
                                """
                                Rewrite the ${if (hasSelection) "selected code" else "entire file"} for this request: ${editorState.geminiPrompt}

                                CRITICAL: Return ONLY the replacement code/text. 
                                DO NOT include any preamble, markdown code fences, or explanations. 
                                The output will be inserted directly into the editor.

                                Project root: ${currentProjectDir()}
                                File: ${file.getAbsolutePath()}
                                Input:
                                ```
                                $contextText
                                ```
                                """
                                    .trimIndent(),
                            mode = GeminiActionMode.Apply,
                            applyResult = { replacement ->
                                pendingPatch =
                                    GeminiPendingPatch(
                                        title = if (hasSelection) "Review selected-code rewrite" else "Review file rewrite",
                                        oldText = contextText,
                                        newText = replacement,
                                        apply = { editor.text.replace(start, end, replacement) },
                                    )
                            },
                        )
                    },
                ) {
                    Text(strings.apply.getString())
                }

                TextButton(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank() && currentEditor() != null,
                    onClick = {
                        val editor = currentEditor() ?: return@TextButton
                        val contextText = selectedOrFileText()
                        runGemini(
                            prompt =
                                """
                                Generate code/text for this request: ${editorState.geminiPrompt}

                                CRITICAL: Return ONLY the code/text to insert. 
                                DO NOT include any preamble, markdown code fences, or explanations. 
                                The output will be inserted directly into the editor.

                                Project root: ${currentProjectDir()}
                                Nearby context from ${file.getName()}:
                                ```
                                $contextText
                                ```
                                """
                                    .trimIndent(),
                            mode = GeminiActionMode.Insert,
                            applyResult = { insertion ->
                                pendingPatch =
                                    GeminiPendingPatch(
                                        title = "Review insertion",
                                        oldText = "",
                                        newText = insertion,
                                        apply = {
                                            editor.text.insert(
                                                editor.cursor.leftLine,
                                                editor.cursor.leftColumn,
                                                insertion,
                                            )
                                        },
                                    )
                            },
                        )
                    },
                ) {
                    Text(strings.insert.getString())
                }

                TextButton(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank(),
                    onClick = {
                        runGemini(
                            prompt =
                                """
                                Act as a Gemini CLI coding agent for this project.
                                User request: ${editorState.geminiPrompt}

                                Use the full codebase under the project root. You may inspect files, search, and edit project files as needed.
                                After changes, summarize exactly what changed and list modified files.

                                Project root: ${currentProjectDir()}
                                Current file: ${file.getAbsolutePath()}
                                Current editor context:
                                ```
                                ${selectedOrFileText()}
                                ```
                                """
                                    .trimIndent(),
                            mode = GeminiActionMode.Agent,
                            applyResult = { _ ->
                                viewModel.tabs.filterIsInstance<EditorTab>().forEach { tab -> tab.refresh() }
                            },
                        )
                    },
                ) {
                    Text("Agent")
                }

                TextButton(enabled = activity != null, onClick = { openFullCli() }) { Text("CLI") }

                TextButton(
                    enabled = editorState.geminiOutput.isNotBlank(),
                    onClick = {
                        ClipboardUtils.copyText("Gemini log", editorState.geminiRawLog.ifBlank { editorState.geminiOutput })
                        toast(strings.copied)
                    },
                ) {
                    Text("Copy log")
                }

                TextButton(
                    enabled = editorState.geminiOutput.isNotBlank(),
                    onClick = {
                        editorState.geminiOutput = ""
                        editorState.geminiRawLog = ""
                        editorState.geminiCliTranscript = ""
                    },
                ) {
                    Text("Clear")
                }
            }

            if (editorState.geminiRunning) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(strings.wait.getString())
                }
            }

            if (editorState.geminiOutput.isNotBlank()) {
                Text(
                    text = editorState.geminiOutput,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    pendingPatch?.let { patch ->
        AlertDialog(
            onDismissRequest = { pendingPatch = null },
            title = { Text(patch.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (patch.oldText.isNotBlank()) {
                        Text("Current", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = patch.oldText,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("Proposed", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = patch.newText,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        patch.apply()
                        pendingPatch = null
                    }
                ) {
                    Text(strings.apply.getString())
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            ClipboardUtils.copyText("Gemini proposed patch", patch.newText)
                            toast(strings.copied)
                        }
                    ) {
                        Text("Copy")
                    }
                    TextButton(onClick = { pendingPatch = null }) { Text(strings.cancel.getString()) }
                }
            },
        )
    }
}
