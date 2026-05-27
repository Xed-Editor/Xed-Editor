package com.rk.tabs.editor

import android.content.Context
import android.graphics.Typeface
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
import com.rk.editor.FontCache
import com.rk.settings.Settings
import com.rk.settings.editor.DEFAULT_TERMINAL_FONT_PATH
import com.rk.settings.terminal.TerminalCursorStyle
import com.rk.terminal.virtualkeys.SpecialButton
import com.rk.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.virtualkeys.VirtualKeysView
import com.rk.theme.LocalThemeHolder
import com.rk.utils.dpToPx
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.lang.ref.WeakReference
import java.util.Properties
import kotlinx.coroutines.launch

class AgentTerminalClient(
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

private data class SheetUiState(
    val terminalHeight: Dp,
    val onDragStart: () -> Unit,
    val onDrag: (Float) -> Unit,
    val onDragEndForInline: () -> Unit,
    val onDragEndForModal: (() -> Unit)? = null,
)

@Composable
private fun rememberGeminiSheetUiState(
    minHeight: Dp = 320.dp,
    initialHeight: Dp = 560.dp,
    onModalMinimize: (() -> Unit)? = null,
    onModalExpand: (() -> Unit)? = null,
): SheetUiState {
    val density = LocalDensity.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.90f).dp
    var terminalHeight by remember { mutableStateOf(initialHeight.coerceIn(minHeight, maxHeight)) }
    var handleDrag by remember { mutableStateOf(0f) }

    return SheetUiState(
        terminalHeight = terminalHeight,
        onDragStart = { handleDrag = 0f },
        onDrag = { dragAmount ->
            handleDrag += dragAmount
            terminalHeight = (terminalHeight - with(density) { dragAmount.toDp() }).coerceIn(minHeight, maxHeight)
        },
        onDragEndForInline = {
            // Snapping or other logic can go here if needed in the future
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
fun AgentCliSheet(
    onDismissRequest: () -> Unit,
    cwd: String,
    session: TerminalSession?,
    modifier: Modifier = Modifier,
    showTerminal: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null,
    controls: (@Composable RowScope.() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val ui = rememberGeminiSheetUiState()

    AgentCliSheetContent(
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
        bottomBar = bottomBar,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCliModalSheet(
    onDismissRequest: () -> Unit,
    cwd: String,
    session: TerminalSession?,
    modifier: Modifier = Modifier,
    showTerminal: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null,
    controls: (@Composable RowScope.() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
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
        AgentCliSheetContent(
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
            bottomBar = bottomBar,
            content = content,
        )
    }
}

@Composable
private fun AgentCliSheetContent(
    onDismissRequest: () -> Unit,
    cwd: String,
    session: TerminalSession?,
    modifier: Modifier = Modifier,
    terminalHeight: Dp = 595.dp,
    showTerminal: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null,
    controls: (@Composable RowScope.() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colorScheme.surfaceContainerHighest, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Unified Header Row with Drag Support
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
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
                    Text("AI Agent", color = colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = cwd.split("/").lastOrNull()?.takeIf { it.isNotBlank() } ?: "/",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier.background(colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                
                // Right: Controls + Hide
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    controls?.invoke(this)
                    IconButton(onClick = onDismissRequest, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Hide", tint = colorScheme.onSurfaceVariant)
                    }
                }
            }

            headerContent?.invoke()

            if (showTerminal) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = colorScheme.outlineVariant.copy(alpha = 0.3f))
                AgentSheetTerminal(session = session, modifier = Modifier.fillMaxWidth(), height = terminalHeight)
            } else {
                content?.invoke(this)
            }

            bottomBar?.let {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = colorScheme.outlineVariant.copy(alpha = 0.3f))
                it()
            }
        }
    }
}

@Composable
fun AgentSheetTerminal(session: TerminalSession?, modifier: Modifier = Modifier, height: Dp = 595.dp) {
    val colorScheme = MaterialTheme.colorScheme
    val currentTheme = LocalThemeHolder.current
    val isDarkMode = isSystemInDarkTheme()
    val keysHeight = 75.dp
    val terminalBodyHeight = (height - keysHeight).coerceAtLeast(160.dp)
    
    if (session == null) {
        Box(
            modifier = modifier.height(height).background(colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Agent CLI stopped", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    var virtualKeysViewRef by remember { mutableStateOf<WeakReference<VirtualKeysView>>(WeakReference(null)) }
    var terminalClient by remember { mutableStateOf<AgentTerminalClient?>(null) }
    var lastBoundSession by remember { mutableStateOf<TerminalSession?>(null) }

    Column(
        modifier = modifier.height(height),
    ) {
        AndroidView<TerminalView>(
            modifier = Modifier
                .fillMaxWidth()
                .height(terminalBodyHeight),
            factory = { context ->
                TerminalView(context, null).apply {
                    val client = AgentTerminalClient(this) { virtualKeysViewRef.get() }
                    terminalClient = client
                    setTerminalViewClient(client)
                    session?.updateTerminalSessionClient(client)
                    
                    setTextSize(dpToPx(Settings.terminal_font_size.toFloat(), context))
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
                    addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        val widthChanged = (right - left) != (oldRight - oldLeft)
                        val heightChanged = (bottom - top) != (oldBottom - oldTop)
                        if (widthChanged || heightChanged) {
                            applyGeminiSheetTerminalColors(
                                onSurfaceColor = colorScheme.onSurface.toArgb(),
                                surfaceColor = colorScheme.surface.toArgb(),
                                terminalColors = if (isDarkMode) currentTheme.darkTerminalColors else currentTheme.lightTerminalColors,
                            )
                        }
                    }
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
                        view.isFocusable = true
                        view.isFocusableInTouchMode = true
                        view.requestFocus()
                        val inputMethodManager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        inputMethodManager?.restartInput(view)
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
