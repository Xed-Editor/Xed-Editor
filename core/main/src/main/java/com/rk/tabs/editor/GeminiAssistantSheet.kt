package com.rk.tabs.editor

import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.ClipboardUtils
import com.rk.ai.GeminiBridge
import com.rk.ai.GeminiCli
import com.rk.editor.FontCache
import com.rk.exec.getDefaultBindings
import com.rk.exec.ShellUtils
import com.rk.exec.TerminalCommand
import com.rk.exec.launchTerminal
import com.rk.file.child
import com.rk.file.localDir
import com.rk.file.localBinDir
import com.rk.file.localLibDir
import com.rk.file.sandboxHomeDir
import com.rk.file.sandboxDir
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.editor.DEFAULT_TERMINAL_FONT_PATH
import com.rk.terminal.TerminalBackEnd
import com.rk.terminal.setupTerminalFiles
import com.rk.terminal.terminalView
import com.rk.theme.LocalThemeHolder
import com.rk.utils.toast
import com.rk.utils.getSourceDirOfPackage
import com.rk.utils.getTempDir
import com.rk.utils.isFDroid
import com.rk.xededitor.BuildConfig
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import java.io.File
import java.lang.ref.WeakReference
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val currentTheme = LocalThemeHolder.current
    var pendingPatch by remember { mutableStateOf<GeminiPendingPatch?>(null) }
    var embeddedGeminiSession by remember { mutableStateOf<TerminalSession?>(null) }

    fun currentEditor() = editorState.editor.get()

    fun selectedOrFileText(): String {
        val editor = currentEditor() ?: return editorState.content?.toString().orEmpty()
        return if (editor.isTextSelected) {
            editor.text.substring(editor.cursorRange.startIndex, editor.cursorRange.endIndex)
        } else {
            editor.text.toString()
        }
    }

    fun currentProjectDir(): String =
        projectRoot?.getAbsolutePath()
            ?: file.getParentFile()?.getAbsolutePath()
            ?: file.getAbsolutePath()

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
        /flow [request]        Open persistent Gemini CLI flow with Xed context
        /sheet <request>       Ask once in the compact Xed sheet
        /sync                  Save dirty open editors so Gemini reads latest content
        /refresh               Refresh clean open editors after Gemini edits files
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

        Embedded Gemini CLI is bound to Xed editors: dirty files sync before prompts, clean open tabs refresh after disk edits, and IDE tools can open review diffs.
        Smart Xed actions: Terminal Ask, Apply, Insert, Agent.
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
        editorState.showGeminiAssistant = false
    }

    fun recentConversationContext(): String =
        editorState.geminiCliTranscript
            .takeLast(12 * 1024)
            .takeIf { it.isNotBlank() }
            ?.let {
                """
                Recent Xed Gemini conversation:
                ```
                $it
                ```
                """
                    .trimIndent()
            }
            .orEmpty()

    fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    fun geminiTerminalPrompt(request: String): String {
        val editor = currentEditor()
        val contextText = selectedOrFileText().take(32 * 1024)
        return """
            You are running inside Xed-Editor's embedded Gemini CLI terminal.
            Keep using Gemini CLI's normal terminal UI and continue interactively after the answer.

            Project root: ${currentProjectDir()}
            Current file: ${file.getAbsolutePath()}
            Current ${if (editor?.isTextSelected == true) "selection" else "file/context"}:
            ```
            $contextText
            ```

            ${recentConversationContext()}

            User request:
            $request
        """
            .trimIndent()
    }

    fun geminiStartupPrompt(): String =
        """
        You are Gemini CLI embedded directly inside Xed-Editor's AI sheet.

        Xed editor binding is active:
        - The current project root is ${currentProjectDir()}.
        - The active file is ${file.getAbsolutePath()}.
        - The Xed IDE bridge is available through Gemini CLI IDE integration.
        - Prefer IDE/editor tools for edits so Xed can review and update open tabs.
        - Before reading files, use the IDE context/open-file tools when relevant because open editors may contain unsaved content.
        - When changing an open file, use IDE diff/write/editor tools when available.
        - If you edit files directly on disk, call the IDE refresh tool or ask Xed to refresh open editors.

        Start by briefly saying you are connected to Xed and ask what the user wants to edit.
        """
            .trimIndent()

    suspend fun saveDirtyOpenEditorsForGemini(): Int {
        val dirtyTabs = viewModel.tabs.filterIsInstance<EditorTab>().filter { it.editorState.isDirty }
        withContext(Dispatchers.Main) {
            dirtyTabs.forEach { tab ->
                tab.editorState.editor.get()?.let { editor -> tab.editorState.content = editor.text }
            }
        }
        dirtyTabs.forEach { it.quickSave() }
        return dirtyTabs.size
    }

    fun refreshCleanOpenEditorsFromDisk() {
        viewModel.tabs.filterIsInstance<EditorTab>()
            .filterNot { it.editorState.isDirty }
            .forEach { it.refresh() }
    }

    fun buildGeminiSheetEnv(workingDir: String, bridge: GeminiBridge.Info): Array<String> {
        val currentActivity = activity ?: return emptyArray()
        val tmpDir = File(getTempDir(), "terminal/gemini-sheet").apply { mkdirs() }
        val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"
        return mutableListOf(
            "PROOT_TMP_DIR=${tmpDir.absolutePath}",
            "WKDIR=$workingDir",
            "PUBLIC_HOME=${currentActivity.getExternalFilesDir(null)?.absolutePath}",
            "COLORTERM=truecolor",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "DEBUG=${BuildConfig.DEBUG}",
            "LOCAL=${localDir().absolutePath}",
            "PRIVATE_DIR=${currentActivity.filesDir.parentFile!!.absolutePath}",
            "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
            "EXT_HOME=${sandboxHomeDir()}",
            "HOME=${if (Settings.sandbox) "/home" else sandboxHomeDir().absolutePath}",
            "PROMPT_DIRTRIM=2",
            "LINKER=$linker",
            "NATIVE_LIB_DIR=${currentActivity.applicationInfo.nativeLibraryDir}",
            "FDROID=$isFDroid",
            "SANDBOX=${Settings.sandbox}",
            "TMP_DIR=${getTempDir()}",
            "TMPDIR=${getTempDir()}",
            "TZ=UTC",
            "DOTNET_GCHeapHardLimit=1C0000000",
            "SOURCE_DIR=${currentActivity.applicationInfo.sourceDir}",
            "TERMUX_X11_SOURCE_DIR=${getSourceDirOfPackage(currentActivity, "com.termux.x11").orEmpty()}",
            "DISPLAY=:0",
            "PATH=${System.getenv("PATH")}:${localBinDir().absolutePath}",
            "ANDROID_ART_ROOT=${System.getenv("ANDROID_ART_ROOT").orEmpty()}",
            "ANDROID_DATA=${System.getenv("ANDROID_DATA").orEmpty()}",
            "ANDROID_I18N_ROOT=${System.getenv("ANDROID_I18N_ROOT").orEmpty()}",
            "ANDROID_ROOT=${System.getenv("ANDROID_ROOT").orEmpty()}",
            "ANDROID_RUNTIME_ROOT=${System.getenv("ANDROID_RUNTIME_ROOT").orEmpty()}",
            "ANDROID_TZDATA_ROOT=${System.getenv("ANDROID_TZDATA_ROOT").orEmpty()}",
            "BOOTCLASSPATH=${System.getenv("BOOTCLASSPATH").orEmpty()}",
            "DEX2OATBOOTCLASSPATH=${System.getenv("DEX2OATBOOTCLASSPATH").orEmpty()}",
            "EXTERNAL_STORAGE=${System.getenv("EXTERNAL_STORAGE").orEmpty()}",
            "GEMINI_CLI_IDE_SERVER_PORT=${bridge.port}",
            "GEMINI_CLI_IDE_AUTH_TOKEN=${bridge.token}",
            "GEMINI_CLI_IDE_PID=${android.os.Process.myPid()}",
            "GEMINI_CLI_IDE_WORKSPACE_PATH=${bridge.workspacePath}",
        )
            .apply {
                if (!isFDroid) {
                    add("PROOT_LOADER=${currentActivity.applicationInfo.nativeLibraryDir}/libproot-loader.so")
                    if (
                        Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() &&
                            File(currentActivity.applicationInfo.nativeLibraryDir).child("libproot-loader32.so").exists()
                    ) {
                        add("PROOT_LOADER32=${currentActivity.applicationInfo.nativeLibraryDir}/libproot-loader32.so")
                    }
                }
                if (Settings.seccomp) add("SECCOMP=1")
            }
            .toTypedArray()
    }

    fun geminiSheetProcess(extraArgs: List<String>, workingDir: String): Pair<String, Array<String>> {
        val tmpDir = File(getTempDir(), "terminal/gemini-sheet-proot").apply { mkdirs() }
        val proot = localBinDir().child("proot").absolutePath
        val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"
        val prootArgs =
            mutableListOf<String>().apply {
                add(proot)
                add("--kill-on-exit")
                add("-w")
                add(workingDir)
                getDefaultBindings().forEach { binding ->
                    if (File(binding.outside).exists()) {
                        add("-b")
                        add("${binding.outside}${binding.inside?.let { ":$it" }.orEmpty()}")
                    }
                }
                if (tmpDir.exists()) {
                    add("-b")
                    add(tmpDir.absolutePath)
                }
                add("-0")
                add("--link2symlink")
                add("--sysvipc")
                add("-L")
                add("-r")
                add(sandboxDir().absolutePath)
                add("/bin/bash")
                add(localBinDir().child("gemini-cli").absolutePath)
                add("--skip-trust")
                add("--include-directories")
                add(workingDir)
                addAll(extraArgs)
            }
        return if (isFDroid) {
            proot to prootArgs.toTypedArray()
        } else {
            linker to (listOf(linker) + prootArgs).toTypedArray()
        }
    }

    fun createEmbeddedGeminiSession(extraArgs: List<String> = emptyList()): TerminalSession? {
        if (activity == null) return null
        setupTerminalFiles()
        val workingDir = currentProjectDir()
        val bridge = GeminiBridge.ensureStarted(viewModel, workingDir)
        val launchArgs =
            extraArgs.ifEmpty {
                listOf("--prompt-interactive", geminiStartupPrompt())
            }
        val (shell, args) = geminiSheetProcess(launchArgs, workingDir)
        val session =
            TerminalSession(
                shell,
                localDir().absolutePath,
                args,
                buildGeminiSheetEnv(workingDir, bridge),
                Settings.terminal_scrollback_buffer,
                TerminalBackEnd(),
            )
        session.mSessionName = "gemini-sheet"
        appendGeminiCli("Embedded Gemini CLI terminal ready.")
        return session
    }

    fun startEmbeddedGemini(extraArgs: List<String> = emptyList()) {
        embeddedGeminiSession?.finishIfRunning()
        embeddedGeminiSession = null
        scope.launch(Dispatchers.IO) {
            val saved = saveDirtyOpenEditorsForGemini()
            withContext(Dispatchers.Main) {
                if (saved > 0) appendGeminiCli("Synced $saved dirty open editor file(s) to disk for Gemini.")
                embeddedGeminiSession = createEmbeddedGeminiSession(extraArgs)
            }
        }
    }

    fun sendPromptToEmbeddedGemini(request: String) {
        if (request.isBlank()) return
        val session = embeddedGeminiSession
        if (session == null || session.emulator == null || !session.isRunning) {
            startEmbeddedGemini(listOf("--prompt-interactive", geminiTerminalPrompt(request)))
        } else {
            scope.launch(Dispatchers.IO) {
                val saved = saveDirtyOpenEditorsForGemini()
                withContext(Dispatchers.Main) {
                    if (saved > 0) appendGeminiCli("Synced $saved dirty open editor file(s) to disk for Gemini.")
                    session.write("${geminiTerminalPrompt(request)}\r")
                }
            }
        }
    }

    fun openInteractiveGeminiCommand(command: String) {
        appendGeminiCli("Opening embedded Gemini CLI for: $command")
        startEmbeddedGemini(listOf("--prompt-interactive", command))
    }

    fun openGeminiFlow(request: String = "") {
        val editor = currentEditor()
        val contextText = selectedOrFileText().take(32 * 1024)
        val prompt =
            """
            You are running as a persistent Gemini CLI coding flow inside Xed-Editor.
            Continue interactively after answering so the user can keep giving follow-up instructions.

            Project root: ${currentProjectDir()}
            Current file: ${file.getAbsolutePath()}
            Current ${if (editor?.isTextSelected == true) "selection" else "file/context"}:
            ```
            $contextText
            ```

            ${recentConversationContext()}

            Initial request:
            ${request.ifBlank { "Start an interactive coding assistant flow. Briefly explain what context you can see and ask what the user wants to do next." }}
            """
                .trimIndent()
        appendGeminiCli("Opening persistent Gemini flow in embedded terminal...")
        startEmbeddedGemini(listOf("--prompt-interactive", prompt))
    }

    fun openGeminiPromptWithArgs(prompt: String, args: List<String>) {
        appendGeminiCli("Opening embedded Gemini CLI: gemini ${args.joinToString(" ")} -p \"${prompt.take(72)}${if (prompt.length > 72) "…" else ""}\"")
        startEmbeddedGemini(args + listOf("-p", prompt))
    }

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

    fun askCurrentPrompt(userRequest: String = editorState.geminiPrompt) {
        val editor = currentEditor()
        val contextText = selectedOrFileText()
        runGemini(
            prompt =
                """
                You are an AI coding assistant inside Xed-Editor.
                User request: $userRequest

                Project root: ${currentProjectDir()}
                File: ${file.getAbsolutePath()}
                ${recentConversationContext()}

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
                startEmbeddedGemini()
                editorState.geminiPrompt = ""
            }

            input == "/flow" -> {
                openGeminiFlow()
                editorState.geminiPrompt = ""
            }

            input.startsWith("/flow ") -> {
                openGeminiFlow(input.removePrefix("/flow ").trim())
                editorState.geminiPrompt = ""
            }

            input.startsWith("/sheet ") -> {
                askCurrentPrompt(input.removePrefix("/sheet ").trim())
                editorState.geminiPrompt = ""
            }

            input == "/sheet" -> {
                appendGeminiCli("Type /sheet <request> to use compact sheet output. Normal Send opens the clean terminal-backed Gemini CLI UI.")
                editorState.geminiPrompt = ""
            }

            input == "/sync" -> {
                scope.launch(Dispatchers.IO) {
                    val saved = saveDirtyOpenEditorsForGemini()
                    withContext(Dispatchers.Main) { appendGeminiCli("Synced $saved dirty open editor file(s) to disk.") }
                }
                editorState.geminiPrompt = ""
            }

            input == "/refresh" -> {
                refreshCleanOpenEditorsFromDisk()
                appendGeminiCli("Refreshing clean open editor tabs from disk.")
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
                appendGeminiCli("Opening embedded Gemini CLI with model: $model")
                startEmbeddedGemini(listOf("--model", model))
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
                startEmbeddedGemini(listOf("--worktree"))
                editorState.geminiPrompt = ""
            }

            input.startsWith("/worktree ") -> {
                startEmbeddedGemini(listOf("--worktree", input.removePrefix("/worktree ").trim()))
                editorState.geminiPrompt = ""
            }

            input == "/debug" -> {
                startEmbeddedGemini(listOf("--debug"))
                editorState.geminiPrompt = ""
            }

            input == "/sandbox" -> {
                startEmbeddedGemini(listOf("--sandbox"))
                editorState.geminiPrompt = ""
            }

            input == "/screen-reader" -> {
                startEmbeddedGemini(listOf("--screen-reader"))
                editorState.geminiPrompt = ""
            }

            input.startsWith("/approval-mode ") -> {
                val mode = input.removePrefix("/approval-mode ").trim()
                startEmbeddedGemini(listOf("--approval-mode", mode))
                editorState.geminiPrompt = ""
            }

            input.startsWith("/extensions use ") -> {
                val extensions = input.removePrefix("/extensions use ").trim()
                startEmbeddedGemini(listOf("--extensions", extensions))
                editorState.geminiPrompt = ""
            }

            input == "/resume latest" -> {
                appendGeminiCli("Opening latest Gemini CLI session in embedded terminal")
                startEmbeddedGemini(listOf("--resume", "latest"))
                editorState.geminiPrompt = ""
            }

            input.startsWith("/resume ") && input != "/resume list" -> {
                val target = input.removePrefix("/resume ").trim()
                appendGeminiCli("Opening Gemini CLI session in embedded terminal: $target")
                startEmbeddedGemini(listOf("--resume", target))
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
                    - writeFile: write file and refresh matching open Xed tab
                    - saveOpenFiles: save dirty open Xed tabs before Gemini reads files
                    - refreshOpenEditors: refresh non-dirty open Xed tabs after disk edits
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
                appendGeminiCli("Unknown Xed Gemini command. Tap CLI for Gemini's full terminal UI, use /flow for terminal-backed AI, /sheet <request> for compact sheet output, or run /help.")
                editorState.geminiPrompt = ""
            }

            else -> {
                sendPromptToEmbeddedGemini(input)
                editorState.geminiPrompt = ""
            }
        }
    }

    @Composable
    fun SectionTitle(text: String) {
        Text(
            text = text,
            color = colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
        )
    }

    @Composable
    fun PromptChip(label: String, prompt: String) {
        TextButton(onClick = { editorState.geminiPrompt = prompt }) {
            Text(label)
        }
    }

    @Composable
    fun CommandChip(label: String, command: String) {
        TextButton(onClick = { editorState.geminiPrompt = command }) {
            Text(label)
        }
    }

    fun geminiCliWelcomeText(): String {
        val tips =
            if (memoryFiles().isEmpty()) {
                "1. Create GEMINI.md files to customize your interactions\n2. /help for more information\n3. Ask coding questions, edit code or run commands\n4. Be specific for the best results"
            } else {
                "1. /help for more information\n2. Ask coding questions, edit code or run commands\n3. Be specific for the best results"
            }
        return """
            Gemini CLI

            Tips for getting started:
            $tips

            Xed additions:
            • Send/Ask opens a real terminal-backed Gemini CLI flow
            • Dirty editor tabs sync before Gemini reads; clean tabs refresh after edits
            • /flow starts a persistent Gemini CLI coding flow
            • /sheet asks once in this compact sheet
            • @path adds file context, ! toggles shell mode
            • Apply/Insert review changes before editing
        """
            .trimIndent()
    }

    @Composable
    fun GeminiFooter() {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Footer",
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("cwd ${currentProjectDir()}", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("mode ${if (editorState.geminiShellMode) "shell" else "default"}", color = if (editorState.geminiShellMode) colorScheme.error else colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Text("edits review", color = colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                Text("model auto", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("${memoryFiles().size} GEMINI.md", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    fun EmbeddedGeminiTerminal() {
        val isDarkMode = isSystemInDarkTheme()

        LaunchedEffect(Unit) {
            if (embeddedGeminiSession == null) startEmbeddedGemini()
        }

        LaunchedEffect(embeddedGeminiSession) {
            while (embeddedGeminiSession?.isRunning == true) {
                delay(1500)
                refreshCleanOpenEditorsFromDisk()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                embeddedGeminiSession?.finishIfRunning()
                embeddedGeminiSession = null
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(colorScheme.surface, RoundedCornerShape(14.dp))
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
        ) {
            val session = embeddedGeminiSession
            if (session == null) {
                Text(
                    text = "Starting embedded Gemini CLI terminal...",
                    modifier = Modifier.padding(16.dp),
                    color = colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                AndroidView(
                    factory = { context ->
                        TerminalView(context, null).apply {
                            terminalView = WeakReference(this)
                            setTextSize(com.rk.utils.dpToPx(Settings.terminal_font_size.toFloat(), context))
                            val fontFile = com.rk.file.sandboxDir().child("etc/font.ttf")
                            if (fontFile.exists()) {
                                setTypeface(Typeface.createFromFile(fontFile))
                            } else {
                                val font =
                                    Settings.terminal_font_path.takeIf { it.isNotEmpty() }?.let {
                                        FontCache.getTypeface(context, it, Settings.is_terminal_font_asset)
                                    } ?: FontCache.getTypeface(context, DEFAULT_TERMINAL_FONT_PATH, true)
                                setTypeface(font)
                            }
                            val client = TerminalBackEnd()
                            session.updateTerminalSessionClient(client)
                            attachSession(session)
                            setTerminalViewClient(client)
                            applyGeminiSheetTerminalColors(
                                surfaceColor = colorScheme.surface.toArgb(),
                                onSurfaceColor = colorScheme.onSurface.toArgb(),
                                terminalColors = if (isDarkMode) currentTheme.darkTerminalColors else currentTheme.lightTerminalColors,
                            )
                            post {
                                keepScreenOn = true
                                isFocusableInTouchMode = true
                                requestFocus()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        terminalView = WeakReference(view)
                        if (view.mTermSession != session) {
                            val client = TerminalBackEnd()
                            session.updateTerminalSessionClient(client)
                            view.attachSession(session)
                            view.setTerminalViewClient(client)
                        }
                        view.applyGeminiSheetTerminalColors(
                            surfaceColor = colorScheme.surface.toArgb(),
                            onSurfaceColor = colorScheme.onSurface.toArgb(),
                            terminalColors = if (isDarkMode) currentTheme.darkTerminalColors else currentTheme.lightTerminalColors,
                        )
                    },
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { editorState.showGeminiAssistant = false },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Text(text = "✦ Gemini CLI", color = colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Terminal backed + Xed bridge", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "cwd ${currentProjectDir()}",
                        color = colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    EmbeddedGeminiTerminal()
                    Text(
                        text = "Notifications",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text =
                            editorState.geminiCliTranscript.ifBlank {
                                geminiCliWelcomeText()
                            },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 96.dp).verticalScroll(rememberScrollState()),
                        color = colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Composer",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
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
                            label = { Text(if (editorState.geminiShellMode) "Shell command" else "Type message, then Send opens Gemini Terminal UI") },
                            minLines = 1,
                            maxLines = 4,
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank(),
                            onClick = { handleGeminiSubmit() },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Terminal")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Using: Terminal Gemini CLI | ${memoryFiles().size} GEMINI.md files | Xed IDE bridge | ${if (editorState.geminiShellMode) "shell mode" else "auto"}",
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
                    GeminiFooter()
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle("Prompt presets")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PromptChip("Explain", "/explain Explain this code and point out important behavior.")
                    PromptChip("Find bugs", "/bugs Find bugs and suggest a safe fix.")
                    PromptChip("Refactor", "/refactor Refactor this code to be cleaner without changing behavior.")
                    PromptChip("Tests", "/tests Generate or improve tests for this code.")
                    PromptChip("Plan", "/plan Analyze this task and propose a safe implementation plan.")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle("Gemini CLI")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CommandChip("Flow", "/flow")
                    CommandChip("Sheet ask", "/sheet ")
                    CommandChip("Sync editors", "/sync")
                    CommandChip("Refresh tabs", "/refresh")
                    CommandChip("Auth", "/auth")
                    CommandChip("Doctor", "/doctor")
                    CommandChip("Sessions", "/sessions list")
                    CommandChip("Resume", "/resume latest")
                    CommandChip("Model", "/model flash")
                    CommandChip("Extensions", "/extensions list")
                    CommandChip("MCP", "/mcp")
                    CommandChip("Memory", "/memory init")
                }
            }

            SectionTitle("Editor actions")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    enabled = !editorState.geminiRunning && editorState.geminiPrompt.isNotBlank(),
                    onClick = { handleGeminiSubmit() },
                ) {
                    Text("Terminal Ask")
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

private fun TerminalView.applyGeminiSheetTerminalColors(onSurfaceColor: Int, surfaceColor: Int, terminalColors: java.util.Properties) {
    onScreenUpdated()
    mEmulator?.mColors?.reset()
    TerminalColors.COLOR_SCHEME.updateWith(terminalColors)
    mEmulator?.mColors?.mCurrentColors?.apply {
        set(TextStyle.COLOR_INDEX_FOREGROUND, onSurfaceColor)
        set(TextStyle.COLOR_INDEX_BACKGROUND, surfaceColor)
        set(TextStyle.COLOR_INDEX_CURSOR, onSurfaceColor)
    }
    invalidate()
}
