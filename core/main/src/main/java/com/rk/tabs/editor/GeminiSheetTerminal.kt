package com.rk.tabs.editor

import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.rk.terminal.TerminalBackEnd
import com.rk.terminal.setupTerminalFiles
import com.rk.terminal.terminalView
import com.rk.terminal.virtualKeysView
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
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import java.io.File
import java.lang.ref.WeakReference
import java.util.Properties
import kotlinx.coroutines.launch

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
    minHeight: Dp = 260.dp,
    initialHeight: Dp = 595.dp,
    onModalMinimize: (() -> Unit)? = null,
    onModalExpand: (() -> Unit)? = null,
): GeminiSheetUiState {
    val density = LocalDensity.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    var minimized by remember { mutableStateOf(false) }
    var terminalHeight by remember { mutableStateOf(initialHeight.coerceAtMost(maxHeight)) }
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
    val colorScheme = MaterialTheme.colorScheme
    val ui = rememberGeminiSheetUiState()

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = if (ui.minimized) 0.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GeminiSheetDragHandle(
            modifier = Modifier.fillMaxWidth(),
            onDragStart = ui.onDragStart,
            onDrag = ui.onDrag,
            onDragEnd = ui.onDragEndForInline,
            backgroundColor = colorScheme.surfaceContainerHighest,
        )
        if (!ui.minimized) {
            GeminiCliSheetContent(
                onDismissRequest = onDismissRequest,
                cwd = cwd,
                session = session,
                terminalHeight = ui.terminalHeight,
                showTerminal = showTerminal,
                headerContent = headerContent,
                controls = controls,
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
    val colorScheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val ui =
        rememberGeminiSheetUiState(
            onModalMinimize = { scope.launch { runCatching { sheetState.partialExpand() } } },
            onModalExpand = { scope.launch { sheetState.expand() } },
        )

    ModalBottomSheet(
        onDismissRequest = {},
        modifier = modifier.fillMaxWidth(),
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            GeminiSheetDragHandle(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colorScheme.surfaceContainerHighest,
                onDragStart = ui.onDragStart,
                onDrag = ui.onDrag,
                onDragEnd = { ui.onDragEndForModal?.invoke() },
            )
        },
    ) {
        GeminiCliSheetContent(
            onDismissRequest,
            cwd,
            session,
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ui.terminalHeight,
            showTerminal,
            headerContent,
            controls,
        )
    }
}

@Composable
private fun GeminiSheetDragHandle(
    modifier: Modifier = Modifier,
    backgroundColor: androidx.compose.ui.graphics.Color,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .background(backgroundColor, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle(
            modifier = Modifier.pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onDragStart() },
                    onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                    onDragEnd = onDragEnd,
                )
            },
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
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
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
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismissRequest) { Text("Hide") }
            }

            headerContent?.invoke()

            if (showTerminal) {
                controls?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        it.invoke(this)
                    }
                }
                Text(
                    text = "cwd $cwd",
                    color = colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )

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

    Column(
        modifier =
            modifier
                .height(height)
                .background(colorScheme.surface, RoundedCornerShape(14.dp))
                .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
    ) {
        if (session == null) {
            Text(
                text = "Starting Gemini CLI...",
                modifier = Modifier.padding(16.dp),
                color = colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(terminalBodyHeight)) {
                AndroidView(
                    factory = { context ->
                        TerminalView(context, null).apply {
                            terminalView = WeakReference(this)
                            setTextSize(dpToPx(Settings.terminal_font_size.toFloat(), context))
                            val fontFile = sandboxDir().child("etc/font.ttf")
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
            AndroidView(
                factory = { context ->
                    VirtualKeysView(context, null).apply {
                        virtualKeysView = WeakReference(this)
                        virtualKeysViewClient = VirtualKeysListener(session)
                        buttonTextColor = colorScheme.onSurface.toArgb()
                        reload(
                            VirtualKeysInfo(
                                Settings.terminal_extra_keys,
                                "",
                                VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(keysHeight),
                update = { keys ->
                    virtualKeysView = WeakReference(keys)
                    keys.virtualKeysViewClient = VirtualKeysListener(session)
                    keys.buttonTextColor = colorScheme.onSurface.toArgb()
                },
            )
        }
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
        TerminalBackEnd(),
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
        "TMP_DIR=${getTempDir()}",
        "TMPDIR=${getTempDir()}",
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
