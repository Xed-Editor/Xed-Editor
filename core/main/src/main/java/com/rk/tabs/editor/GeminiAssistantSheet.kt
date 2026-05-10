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
import kotlinx.coroutines.cancel
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
    val colorScheme = MaterialTheme.colorScheme
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
        val flag = if (mode == GeminiActionMode.Agent) "--approval-mode=auto_edit -p" else "-p"
        return "gemini $flag \"${prompt.lineSequence().firstOrNull().orEmpty().take(72)}${if (prompt.length > 72) "…" else ""}\""
    }

    fun geminiHelpText(): String =
        """
        Gemini CLI commands supported in this Xed sheet:

        /help, /?              Show this help
        /clear                 Clear visible sheet history
        /copy                  Copy last Gemini output/log
        /about                 Show Xed Gemini backend status
        /doctor                Check Node/npm/Gemini CLI install and version
        /auth                  Open Gemini auth flow in the full CLI
        /cli                   Open full interactive Gemini CLI
        /tools [desc]          Show available tool/back-end summary
        /ide status            Show IDE bridge status
        /docs gemini           Show bundled Gemini CLI docs paths
        /sessions list         List saved Gemini sessions
        /session delete <id>   Delete a saved Gemini session
        /resume [latest|id]    Resume a Gemini CLI session in the full CLI
        /extensions list       List available Gemini CLI extensions
        /model <name>          Open CLI with selected model (pro/flash/etc.)
        /plan <request>        Ask Gemini in plan/read-only mode
        /worktree [name]       Open Gemini in worktree mode
        /debug, /sandbox,
        /screen-reader,
        /approval-mode <mode>  Open CLI with these flags
        /model, /mcp,
        /extensions, /privacy,
        /stats, /bug, /theme, /vim,
        /agents, /skills,
        /permissions, /policies,
        /hooks, /restore,
        /rewind, /shells,
        /setup-github,
        /terminal-setup,
        /upgrade, /commands,
        /settings              Open matching Gemini CLI feature
        /directory show        Show active workspace directory
        /memory list           List GEMINI.md files in project parents
        /memory show           Show merged GEMINI.md memory
        /memory add <text>     Append text to project GEMINI.md
        /memory init           Create a project GEMINI.md starter file

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

    fun findUpward(relativePath: String): File? {
        var dir: File? = File(currentProjectDir())
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return null
    }

    fun geminiDocsHint(): String {
        val docsDir = findUpward("help_res/gemini-cli/docs") ?: return ""
        val keyDocs =
            listOf(
                "ide-integration/ide-companion-spec.md",
                "cli/cli-reference.md",
                "cli/headless.md",
                "cli/checkpointing.md",
                "reference/configuration.md",
            )
                .map { File(docsDir, it) }
                .filter { it.exists() }
                .joinToString("\n") { "- ${it.absolutePath}" }
        return """
            Local Gemini CLI documentation is available under:
            ${docsDir.absolutePath}

            Important local docs:
            $keyDocs

            If the user references @help_res, Gemini CLI docs, IDE mode, MCP, headless mode, flags, checkpointing, or Gemini CLI behavior, inspect these local docs before answering or editing.
        """
            .trimIndent()
    }

    fun appendMemory(text: String) {
        val target = File(currentProjectDir(), "GEMINI.md")
        target.parentFile?.mkdirs()
        target.appendText("\n\n$text\n")
        appendGeminiCli("✔ Added memory to ${target.absolutePath}")
    }

    fun initProjectMemory() {
        val target = File(currentProjectDir(), "GEMINI.md")
        if (target.exists()) {
            appendGeminiCli("GEMINI.md already exists: ${target.absolutePath}")
            editorState.geminiOutput = target.readText()
            return
        }
        target.parentFile?.mkdirs()
        val content =
            """
            # Xed-Editor project instructions for Gemini

            - Keep changes minimal and focused.
            - Prefer existing architecture, style, and naming.
            - Inspect relevant files before editing.
            - For Android/Kotlin changes, keep Compose UI theme-aware.
            - After edits, summarize changed files and any commands to verify.
            - Do not modify generated/build files unless explicitly asked.
            """
                .trimIndent()
        target.writeText(content)
        appendGeminiCli("✔ Created ${target.absolutePath}")
        editorState.geminiOutput = content
    }

    fun runShellCommand(command: String) {
        if (command.isBlank() || editorState.geminiRunning) return
        editorState.geminiRunning = true
        appendGeminiCli("${currentProjectDir()} ${'$'} $command")
        val job = scope.launch(Dispatchers.IO) {
            val result = runCatching {
                ShellUtils.runUbuntuStreaming(
                    currentProjectDir(),
                    "/bin/bash",
                    "-lc",
                    command,
                    timeoutSeconds = 120,
                    onStdout = { line -> scope.launch(Dispatchers.Main) { appendGeminiCli(line) } },
                    onStderr = { line -> scope.launch(Dispatchers.Main) { appendGeminiCli(line) } },
                )
            }
            withContext(Dispatchers.Main) {
                editorState.geminiRunning = false
                editorState.geminiJob = null
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
        editorState.geminiJob = job
        job.invokeOnCompletion { cause ->
            if (cause != null) {
                scope.launch(Dispatchers.Main) {
                    editorState.geminiRunning = false
                    editorState.geminiJob = null
                    appendGeminiCli("Cancelled.")
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

        val job = scope.launch(Dispatchers.IO) {
            val workingDir = GeminiCli.workingDirFor(file, projectRoot)
            val bridge = GeminiBridge.ensureStarted(viewModel, workingDir)
            val bridgedPrompt =
                """
                Xed-Editor GUI bridge is available for this session.
                - Context endpoint: ${bridge.url}/context?token=${bridge.token}
                - Refresh editors endpoint: ${bridge.url}/refresh?token=${bridge.token}
                Use the context endpoint when you need current open files, active editor file, cursor, or selected text.
                After editing files, call the refresh endpoint if possible so the Android GUI updates.
                ${geminiDocsHint()}

                $prompt
                """
                    .trimIndent()
            val result =
                runCatching {
                    if (mode == GeminiActionMode.Agent) {
                        GeminiCli.agent(
                            prompt = bridgedPrompt,
                            workingDir = workingDir,
                            projectDir = workingDir,
                            ideBridge = bridge,
                            onOutput = { line -> scope.launch(Dispatchers.Main) { appendGeminiCli(line) } },
                        )
                    } else {
                        GeminiCli.prompt(
                            prompt = bridgedPrompt,
                            workingDir = workingDir,
                            projectDir = workingDir,
                            ideBridge = bridge,
                            onOutput = { line -> scope.launch(Dispatchers.Main) { appendGeminiCli(line) } },
                        )
                    }
                }

            withContext(Dispatchers.Main) {
                editorState.geminiRunning = false
                editorState.geminiJob = null
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
        editorState.geminiJob = job
        job.invokeOnCompletion { cause ->
            if (cause != null) {
                scope.launch(Dispatchers.Main) {
                    editorState.geminiRunning = false
                    editorState.geminiJob = null
                    appendGeminiCli("Cancelled.")
                }
            }
        }
    }

    fun openFullCli(extraArgs: List<String> = emptyList()) {
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
                    ) + extraArgs,
                id = "gemini-cli-project",
                terminatePreviousSession = false,
                workingDir = workingDir,
                env =
                    arrayOf(
                        "GEMINI_CLI_IDE_SERVER_PORT=${bridge.port}",
                        "GEMINI_CLI_IDE_AUTH_TOKEN=${bridge.token}",
                        "GEMINI_CLI_IDE_PID=${android.os.Process.myPid()}",
                        "GEMINI_CLI_IDE_WORKSPACE_PATH=${bridge.workspacePath}",
                    ),
            ),
        )
    }

    fun openInteractiveGeminiCommand(command: String) {
        appendGeminiCli("Opening full Gemini CLI for: $command")
        openFullCli(listOf("--prompt-interactive", command))
    }

    fun openGeminiPromptWithArgs(prompt: String, args: List<String>) {
        appendGeminiCli("Opening Gemini CLI: gemini ${args.joinToString(" ")} -p \"${prompt.take(72)}${if (prompt.length > 72) "…" else ""}\"")
        openFullCli(args + listOf("-p", prompt))
    }

    fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    fun knownInteractiveCommand(input: String): Boolean {
        val command = input.substringBefore(" ")
        return command in
            setOf(
                "/agents",
                "/auth",
                "/bug",
                "/chat",
                "/compress",
                "/commands",
                "/editor",
                "/extensions",
                "/hooks",
                "/ide",
                "/init",
                "/mcp",
                "/model",
                "/permissions",
                "/plan",
                "/policies",
                "/privacy",
                "/restore",
                "/rewind",
                "/resume",
                "/settings",
                "/setup-github",
                "/shells",
                "/skills",
                "/stats",
                "/terminal-setup",
                "/theme",
                "/tools",
                "/upgrade",
                "/vim",
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

            input == "/doctor" || input == "/gemini doctor" -> {
                runShellCommand(
                    """
                    printf 'Node: '; command -v node >/dev/null 2>&1 && node --version || echo missing
                    printf 'npm: '; command -v npm >/dev/null 2>&1 && npm --version || echo missing
                    printf 'Gemini: '; command -v gemini >/dev/null 2>&1 && gemini --version || echo missing
                    printf 'Gemini binary: '; command -v gemini || true
                    printf 'Working directory: '; pwd
                    printf 'IDE bridge port: '; echo '${GeminiBridge.ensureStarted(viewModel, currentProjectDir()).port}'
                    printf 'Auth browser mode: '; [ -n "${'$'}NO_BROWSER" ] && echo manual || echo android/default
                    """
                        .trimIndent(),
                )
                editorState.geminiPrompt = ""
            }

            input == "/auth" -> {
                openInteractiveGeminiCommand("/auth")
                editorState.geminiPrompt = ""
            }

            input == "/cli" || input == "/open cli" -> {
                openFullCli()
                editorState.geminiPrompt = ""
            }

            input == "/sessions list" || input == "/session list" -> {
                runShellCommand("gemini --list-sessions")
                editorState.geminiPrompt = ""
            }

            input.startsWith("/session delete ") || input.startsWith("/sessions delete ") -> {
                val target = input.substringAfter("delete ").trim()
                runShellCommand("gemini --delete-session ${shellQuote(target)}")
                editorState.geminiPrompt = ""
            }

            input == "/extensions list" || input == "/extension list" -> {
                runShellCommand("gemini --list-extensions")
                editorState.geminiPrompt = ""
            }

            input.startsWith("/model ") && input.split(Regex("\\s+")).size == 2 -> {
                val model = input.removePrefix("/model ").trim()
                appendGeminiCli("Opening Gemini CLI with model: $model")
                openFullCli(listOf("--model", model))
                editorState.geminiPrompt = ""
            }

            input.startsWith("/plan ") -> {
                val request = input.removePrefix("/plan ").trim()
                if (request.isNotBlank()) {
                    openGeminiPromptWithArgs(request, listOf("--approval-mode=plan"))
                }
                editorState.geminiPrompt = ""
            }

            input == "/worktree" -> {
                openFullCli(listOf("--worktree"))
                editorState.geminiPrompt = ""
            }

            input.startsWith("/worktree ") -> {
                openFullCli(listOf("--worktree", input.removePrefix("/worktree ").trim()))
                editorState.geminiPrompt = ""
            }

            input == "/debug" -> {
                openFullCli(listOf("--debug"))
                editorState.geminiPrompt = ""
            }

            input == "/sandbox" -> {
                openFullCli(listOf("--sandbox"))
                editorState.geminiPrompt = ""
            }

            input == "/screen-reader" -> {
                openFullCli(listOf("--screen-reader"))
                editorState.geminiPrompt = ""
            }

            input.startsWith("/approval-mode ") -> {
                val mode = input.removePrefix("/approval-mode ").trim()
                openFullCli(listOf("--approval-mode", mode))
                editorState.geminiPrompt = ""
            }

            input.startsWith("/extensions use ") -> {
                val extensions = input.removePrefix("/extensions use ").trim()
                openFullCli(listOf("--extensions", extensions))
                editorState.geminiPrompt = ""
            }

            input == "/resume latest" -> {
                appendGeminiCli("Opening latest Gemini CLI session")
                openFullCli(listOf("--resume", "latest"))
                editorState.geminiPrompt = ""
            }

            input.startsWith("/resume ") && input != "/resume list" -> {
                val target = input.removePrefix("/resume ").trim()
                appendGeminiCli("Opening Gemini CLI session: $target")
                openFullCli(listOf("--resume", target))
                editorState.geminiPrompt = ""
            }

            input == "/docs gemini" || input == "/docs" -> {
                val text =
                    geminiDocsHint().ifBlank {
                        "No bundled Gemini CLI docs found under help_res/gemini-cli/docs from ${currentProjectDir()}."
                    }
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
                    - listFiles: lists project directories, optionally recursive
                    - openDiff: opens proposed file content for review before writing
                    - closeDiff: returns final file content
                    - getOpenFiles, getActiveFile, getSelection
                    - replaceSelection: review then replace active editor selection/file
                    - insertAtCursor: review then insert at active cursor
                    - showMessage, runCommand, refreshFile

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

            input == "/memory init" -> {
                initProjectMemory()
                editorState.geminiPrompt = ""
            }

            input == "/memory reload" || input == "/memory refresh" -> {
                val text = "Xed reads GEMINI.md fresh for each request. Current memory files:\n" +
                    memoryFiles().joinToString("\n") { it.absolutePath }.ifBlank { "No GEMINI.md files found." }
                appendGeminiCli(text)
                editorState.geminiOutput = text
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

            input.startsWith("/") && knownInteractiveCommand(input) -> {
                openInteractiveGeminiCommand(input)
                editorState.geminiPrompt = ""
            }

            input.startsWith("/") && listOf("/explain", "/bugs", "/refactor", "/tests").none { input.startsWith(it) } -> {
                appendGeminiCli("Unknown Xed Gemini command. Tap CLI for Gemini's full interactive command set, or run /help.")
                editorState.geminiPrompt = ""
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
                        .background(colorScheme.surfaceContainerHighest, RoundedCornerShape(18.dp))
                        .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                        .padding(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "✦ Gemini CLI", color = colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Xed bridge", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "cwd ${currentProjectDir()}",
                        color = colorScheme.onSurfaceVariant,
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
                                For first-time auth, tap CLI and follow the browser/manual URL prompt.
                                """
                                    .trimIndent()
                            },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp).verticalScroll(rememberScrollState()),
                        color = colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = if (editorState.geminiShellMode) "! " else "> ",
                            color = if (editorState.geminiShellMode) colorScheme.tertiary else colorScheme.primary,
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
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (editorState.geminiShellMode) "shell access" else "review edits",
                            color = if (editorState.geminiShellMode) colorScheme.error else colorScheme.tertiary,
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
                TextButton(onClick = { editorState.geminiPrompt = "/auth" }) {
                    Text("Auth")
                }
                TextButton(onClick = { editorState.geminiPrompt = "/doctor" }) {
                    Text("Doctor")
                }
                TextButton(onClick = { editorState.geminiPrompt = "/sessions list" }) {
                    Text("Sessions")
                }
                TextButton(onClick = { editorState.geminiPrompt = "/memory init" }) {
                    Text("Memory")
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
                    TextButton(onClick = { editorState.geminiJob?.cancel("Cancelled by user") }) {
                        Text("Cancel")
                    }
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
