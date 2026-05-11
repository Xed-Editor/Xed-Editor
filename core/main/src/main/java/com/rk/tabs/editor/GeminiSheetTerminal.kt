package com.rk.tabs.editor

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.blankj.utilcode.util.ClipboardUtils
import com.rk.ai.GeminiBridge
import com.rk.ai.geminiIdeWorkspacePath
import com.rk.editor.FontCache
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.file.localLibDir
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.settings.Settings
import com.rk.settings.editor.DEFAULT_TERMINAL_FONT_PATH
import com.rk.settings.terminal.TerminalCursorStyle
import com.rk.terminal.TerminalBackEnd
import com.rk.terminal.setupTerminalFiles
import com.rk.terminal.virtualkeys.SpecialButton
import com.rk.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.virtualkeys.VirtualKeysView
import com.rk.theme.LocalThemeHolder
import com.rk.utils.dpToPx
import com.rk.utils.getSourceDirOfPackage
import com.rk.utils.getTempDir
import com.rk.utils.isFDroid
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import java.lang.ref.WeakReference
import java.util.Properties
import kotlinx.coroutines.launch

class GeminiTerminalClient(
    private val view: TerminalView,
    private val virtualKeysViewRef: () -> VirtualKeysView?
) : TerminalViewClient, TerminalSessionClient {
    
    override fun onTextChanged(changedSession: TerminalSession) {
        view.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) {}
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ClipboardUtils.copyText("Terminal", text)
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = ClipboardUtils.getText().toString()
        if (clip.trim { it <= ' ' }.isNotEmpty() && view.mEmulator != null) {
            view.mEmulator.paste(clip)
        }
    }
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int {
        return when (Settings.terminal_cursor_style) {
            TerminalCursorStyle.BAR.value -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR
            TerminalCursorStyle.UNDERLINE.value -> TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
            else -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
        }
    }
    override fun logError(tag: String?, message: String?) { Log.e(tag.toString(), message.toString()) }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag.toString(), message.toString()) }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag.toString(), message.toString()) }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag.toString(), message.toString()) }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag.toString(), message.toString()) }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag.toString(), message.toString())
        e?.printStackTrace()
    }
    override fun logStackTrace(tag: String?, e: Exception?) { e?.printStackTrace() }

    override fun onScale(scale: Float): Float {
        val fontScale = scale.coerceIn(11f, 45f)
        view.setTextSize(fontScale.toInt())
        return fontScale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        view.post {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
            val inputMethodManager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputMethodManager?.restartInput(view)
            inputMethodManager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean {
        return virtualKeysViewRef()?.readSpecialButton(SpecialButton.CTRL, true) ?: false
    }
    override fun readAltKey(): Boolean {
        return virtualKeysViewRef()?.readSpecialButton(SpecialButton.ALT, true) ?: false
    }
    override fun readShiftKey(): Boolean {
        return virtualKeysViewRef()?.readSpecialButton(SpecialButton.SHIFT, true) ?: false
    }
    override fun readFnKey(): Boolean {
        return virtualKeysViewRef()?.readSpecialButton(SpecialButton.FN, true) ?: false
    }
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {
        view.setTerminalCursorBlinkerState(true, true)
    }
}

private data class GeminiSheetUiState(
    val minimized: Boolean,
    val terminalHeight: Dp,
    val onDragStart: () -> Unit,
    val onDrag: (Float) -> Unit,
    val onDragEndForInline: () -> Unit,
    val onDragEndForModal: (() -> Unit)? = null,
)

@Composable
private fun rememberGeminiSheetUiState(
    minHeight: Dp = 220.dp,
    initialHeight: Dp = 560.dp,
    onModalMinimize: (() -> Unit)? = null,
    onModalExpand: (() -> Unit)? = null,
): GeminiSheetUiState {
    val density = LocalDensity.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.90f).dp
    var minimized by remember { mutableStateOf(false) }
    var terminalHeight by remember { mutableStateOf(initialHeight.coerceIn(minHeight, maxHeight)) }
    var handleDrag by remember { mutableStateOf(0f) }

    return GeminiSheetUiState(
        minimized = minimized,
        terminalHeight = terminalHeight,
        onDragStart = { handleDrag = 0f },
        onDrag = { dragAmount ->
            handleDrag += dragAmount
            terminalHeight = (terminalHeight - with(density) { dragAmount.toDp() }).coerceIn(minHeight, maxHeight)
        },
        onDragEndForInline = {
            when {
                handleDrag > 140f -> minimized = true
                handleDrag < -30f -> minimized = false
            }
        },
        onDragEndForModal = {
            when {
                handleDrag > 60f -> onModalMinimize?.invoke()
                handleDrag < -60f -> onModalExpand?.invoke()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiCliSheet(
    onDismissRequest: () -> Unit,
    cwd: String,
    session: TerminalSession?,
    modifier: Modifier = Modifier,
    showTerminal: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null,
    controls: (@Composable RowScope.() -> Unit)? = null,
) {
    val ui = rememberGeminiSheetUiState()

    if (!ui.minimized) {
        GeminiCliSheetContent(
            onDismissRequest = onDismissRequest,
            cwd = cwd,
            session = session,
            modifier = modifier,
            terminalHeight = ui.terminalHeight,
            showTerminal = showTerminal,
            headerContent = headerContent,
            controls = controls,
            onDragStart = ui.onDragStart,
            onDrag = ui.onDrag,
            onDragEnd = ui.onDragEndForInline,
        )
    } else {
        // Minimized handle
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { ui.onDragStart() },
                        onVerticalDrag = { _, dragAmount -> ui.onDrag(dragAmount) },
                        onDragEnd = ui.onDragEndForInline,
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiCliModalSheet(
    onDismissRequest: () -> Unit,
    cwd: String,
    session: TerminalSession?,
    modifier: Modifier = Modifier,
    showTerminal: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null,
    controls: (@Composable RowScope.() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val ui =
        rememberGeminiSheetUiState(
            onModalMinimize = { scope.launch { runCatching { sheetState.partialExpand() } } },
            onModalExpand = { scope.launch { sheetState.expand() } },
        )
    LaunchedEffect(Unit) {
        runCatching { sheetState.expand() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.fillMaxWidth(),
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
    ) {
        GeminiCliSheetContent(
            onDismissRequest = onDismissRequest,
            cwd = cwd,
            session = session,
            modifier = Modifier.fillMaxWidth(),
            terminalHeight = ui.terminalHeight,
            showTerminal = showTerminal,
            headerContent = headerContent,
            controls = controls,
            onDragStart = ui.onDragStart,
            onDrag = ui.onDrag,
            onDragEnd = { ui.onDragEndForModal?.invoke() },
        )
    }
}

@Composable
private fun GeminiCliSheetContent(
    onDismissRequest: () -> Unit,
    cwd: String,
    session: TerminalSession?,
    modifier: Modifier = Modifier,
    terminalHeight: Dp = 595.dp,
    showTerminal: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null,
    controls: (@Composable RowScope.() -> Unit)? = null,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colorScheme.surfaceContainerHighest, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Unified Header Row with Drag Support
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { onDragStart() },
                            onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                            onDragEnd = onDragEnd,
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Title + CWD
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text("Gemini", color = colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = cwd.split("/").lastOrNull()?.takeIf { it.isNotBlank() } ?: "/",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.background(colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                
                // Center & Right: Controls + Hide
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    controls?.invoke(this)
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Hide", tint = colorScheme.onSurfaceVariant)
                    }
                }
            }

            headerContent?.invoke()

            if (showTerminal) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = colorScheme.outlineVariant.copy(alpha = 0.3f))
                GeminiSheetTerminal(session = session, modifier = Modifier.fillMaxWidth(), height = terminalHeight)
            }
        }
    }
}

@Composable
fun GeminiSheetTerminal(session: TerminalSession?, modifier: Modifier = Modifier, height: Dp = 595.dp) {
    val colorScheme = MaterialTheme.colorScheme
    val currentTheme = LocalThemeHolder.current
    val isDarkMode = isSystemInDarkTheme()
    val keysHeight = 75.dp
    val terminalBodyHeight = (height - keysHeight).coerceAtLeast(160.dp)
    
    var virtualKeysViewRef by remember { mutableStateOf<WeakReference<VirtualKeysView>>(WeakReference(null)) }
    var terminalClient by remember { mutableStateOf<GeminiTerminalClient?>(null) }
    var lastBoundSession by remember { mutableStateOf<TerminalSession?>(null) }

    Column(
        modifier = modifier.height(height),
    ) {
        AndroidView<TerminalView>(
            modifier = Modifier.fillMaxWidth().height(terminalBodyHeight),
            factory = { context ->
                TerminalView(context, null).apply {
                    val client = GeminiTerminalClient(this) { virtualKeysViewRef.get() }
                    terminalClient = client
                    setTerminalViewClient(client)
                    session?.updateTerminalSessionClient(client)
                    
                    setTextSize(Settings.terminal_font_size)
                    runCatching {
                        val fontPath = Settings.terminal_font_path.ifEmpty { DEFAULT_TERMINAL_FONT_PATH }
                        val font = FontCache.getTypeface(context, fontPath, Settings.is_terminal_font_asset) ?: Typeface.MONOSPACE
                        setTypeface(font)
                    }
                    applyGeminiSheetTerminalColors(
                        onSurfaceColor = colorScheme.onSurface.toArgb(),
                        surfaceColor = colorScheme.surface.toArgb(),
                        terminalColors = if (isDarkMode) currentTheme.darkTerminalColors else currentTheme.lightTerminalColors,
                    )
                    attachSession(session)
                    lastBoundSession = session
                    post {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        requestFocus()
                        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        inputMethodManager?.restartInput(this)
                    }
                }
            },
            update = { view ->
                if (session !== lastBoundSession) {
                    terminalClient?.let { client ->
                        session?.updateTerminalSessionClient(client)
                        view.setTerminalViewClient(client)
                    }
                    view.post {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        requestFocus()
                        val inputMethodManager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        inputMethodManager?.restartInput(this)
                    }
                    lastBoundSession = session
                }
                view.attachSession(session)
                view.applyGeminiSheetTerminalColors(
                    onSurfaceColor = colorScheme.onSurface.toArgb(),
                    surfaceColor = colorScheme.surface.toArgb(),
                    terminalColors = if (isDarkMode) currentTheme.darkTerminalColors else currentTheme.lightTerminalColors,
                )
            },
        )

        AndroidView<VirtualKeysView>(
            modifier = Modifier.fillMaxWidth().height(keysHeight),
            factory = { context ->
                VirtualKeysView(context, null).apply {
                    setButtonTextColor(colorScheme.onSurface.toArgb())
                    runCatching {
                        val info = VirtualKeysInfo(
                            Settings.terminal_extra_keys,
                            "",
                            VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                        )
                        reload(info)
                    }
                    virtualKeysViewRef = WeakReference(this)
                }
            },
            update = { keys ->
                keys.setVirtualKeysViewClient(session?.let { VirtualKeysListener(it) })
                keys.setButtonTextColor(colorScheme.onSurface.toArgb())
            },
        )
    }
}

fun createGeminiSheetSession(
    activity: Activity,
    bridge: GeminiBridge.Info,
    workingDir: String,
    extraArgs: List<String> = emptyList(),
): TerminalSession {
    setupTerminalFiles()
    val (shell, args) = geminiSheetProcessArgs(extraArgs, workingDir)
    return TerminalSession(
        shell,
        localDir().absolutePath,
        args,
        buildGeminiSheetEnv(activity, workingDir, bridge),
        Settings.terminal_scrollback_buffer,
        TerminalBackEnd(), // Safe default client to avoid NPE during initialization
    ).also { it.mSessionName = "gemini-sheet" }
}

private fun geminiSheetProcessArgs(extraArgs: List<String>, workingDir: String): Pair<String, Array<String>> {
    val sandbox = localBinDir().child("sandbox").absolutePath
    val geminiLauncher = localBinDir().child("gemini-cli").absolutePath
    val command =
        listOf(
            sandbox,
            "/bin/bash",
            geminiLauncher,
            "--skip-trust",
            "--include-directories",
            workingDir,
        ) + extraArgs
    return "/system/bin/sh" to arrayOf("sh", *command.toTypedArray())
}

private fun buildGeminiSheetEnv(activity: Activity, workingDir: String, bridge: GeminiBridge.Info): Array<String> {
    val tmpDir = File(getTempDir(), "terminal/gemini-sheet").apply { mkdirs() }
    val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"
    return mutableListOf(
        "PROOT_TMP_DIR=${tmpDir.absolutePath}",
        "WKDIR=$workingDir",
        "PUBLIC_HOME=${activity.getExternalFilesDir(null)?.absolutePath}",
        "COLORTERM=truecolor",
        "TERM=xterm-256color",
        "TERM_PROGRAM=vscode",
        "TERM_PROGRAM_VERSION=1.0.0",
        "VSCODE_PID=${android.os.Process.myPid()}",
        "EDITOR=vim",
        "VISUAL=vim",
        "LANG=C.UTF-8",
        "DEBUG=${System.getenv("XED_GEMINI_DEBUG") ?: "true"}",
        "DEBUG_MODE=${System.getenv("XED_GEMINI_DEBUG") ?: "true"}",
        "GEMINI_DEBUG_LOG_FILE=${System.getenv("XED_GEMINI_DEBUG_LOG_FILE") ?: "/home/.gemini/xed-debug.log"}",
        "GEMINI_CONTEXT_TRACE_DIR=${System.getenv("XED_GEMINI_CONTEXT_TRACE_DIR") ?: "/home/.gemini/xed-traces"}",
        "LOCAL=${localDir().absolutePath}",
        "PRIVATE_DIR=${activity.filesDir.parentFile!!.absolutePath}",
        "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
        "EXT_HOME=${sandboxHomeDir()}",
        "HOME=${if (Settings.sandbox) "/home" else sandboxHomeDir().absolutePath}",
        "PROMPT_DIRTRIM=2",
        "LINKER=$linker",
        "NATIVE_LIB_DIR=${activity.applicationInfo.nativeLibraryDir}",
        "FDROID=$isFDroid",
        "SANDBOX=${Settings.sandbox}",
        "TMP_DIR=${tmpDir.absolutePath}",
        "TMPDIR=${tmpDir.absolutePath}",
        "TZ=UTC",
        "DOTNET_GCHeapHardLimit=1C0000000",
        "SOURCE_DIR=${activity.applicationInfo.sourceDir}",
        "TERMUX_X11_SOURCE_DIR=${getSourceDirOfPackage(activity, "com.termux.x11").orEmpty()}",
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
        "GEMINI_CLI_IDE_WORKSPACE_PATH=${geminiIdeWorkspacePath(workingDir)}",
    ).apply {
        if (!isFDroid) {
            add("PROOT_LOADER=${activity.applicationInfo.nativeLibraryDir}/libproot-loader.so")
            if (Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() && File(activity.applicationInfo.nativeLibraryDir).child("libproot-loader32.so").exists()) {
                add("PROOT_LOADER32=${activity.applicationInfo.nativeLibraryDir}/libproot-loader32.so")
            }
        }
        if (Settings.seccomp) add("SECCOMP=1")
    }.toTypedArray()
}

private fun TerminalView.applyGeminiSheetTerminalColors(onSurfaceColor: Int, surfaceColor: Int, terminalColors: Properties) {
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
