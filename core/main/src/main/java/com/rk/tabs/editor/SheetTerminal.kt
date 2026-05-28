package com.rk.tabs.editor

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.blankj.utilcode.util.ClipboardUtils
import com.rk.settings.Settings
import com.rk.settings.terminal.TerminalCursorStyle
import com.rk.terminal.applyTerminalColors
import com.rk.terminal.applyTerminalSettings
import com.rk.terminal.virtualkeys.*
import com.rk.theme.LocalThemeHolder
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.lang.ref.WeakReference

class SheetTerminalClient(
    private val view: TerminalView,
    private val virtualKeysViewRef: () -> VirtualKeysView?,
) : TerminalViewClient, TerminalSessionClient {

    override fun onTextChanged(changedSession: TerminalSession) { view.onScreenUpdated() }
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
    override fun getTerminalCursorStyle(): Int = when (Settings.terminal_cursor_style) {
        TerminalCursorStyle.BAR.value -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR
        TerminalCursorStyle.UNDERLINE.value -> TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
        else -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
    }
    override fun logError(tag: String?, message: String?) { Log.e(tag.toString(), message.toString()) }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag.toString(), message.toString()) }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag.toString(), message.toString()) }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag.toString(), message.toString()) }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag.toString(), message.toString()) }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag.toString(), message.toString()); e?.printStackTrace()
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
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.restartInput(view); imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
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
    override fun readControlKey(): Boolean = virtualKeysViewRef()?.readSpecialButton(SpecialButton.CTRL, true) ?: false
    override fun readAltKey(): Boolean = virtualKeysViewRef()?.readSpecialButton(SpecialButton.ALT, true) ?: false
    override fun readShiftKey(): Boolean = virtualKeysViewRef()?.readSpecialButton(SpecialButton.SHIFT, true) ?: false
    override fun readFnKey(): Boolean = virtualKeysViewRef()?.readSpecialButton(SpecialButton.FN, true) ?: false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() { view.setTerminalCursorBlinkerState(true, true) }
}

@Composable
fun SheetTerminal(
    session: TerminalSession?,
    modifier: Modifier = Modifier,
    showKeys: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val currentTheme = LocalThemeHolder.current
    val isDarkMode = isSystemInDarkTheme()

    if (session == null) {
        Box(
            modifier = modifier.fillMaxSize().background(colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Terminal stopped",
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    var virtualKeysViewRef by remember { mutableStateOf<WeakReference<VirtualKeysView>>(WeakReference(null)) }
    var terminalClient by remember { mutableStateOf<SheetTerminalClient?>(null) }
    var lastBoundSession by remember { mutableStateOf<TerminalSession?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        AndroidView<TerminalView>(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                TerminalView(context, null).apply {
                    val client = SheetTerminalClient(this) { virtualKeysViewRef.get() }
                    terminalClient = client
                    setTerminalViewClient(client)
                    session?.updateTerminalSessionClient(client)
                    applyTerminalSettings(context)
                    applyTerminalColors(
                        onSurfaceColor = colorScheme.onSurface.toArgb(),
                        surfaceColor = colorScheme.surface.toArgb(),
                        terminalColors = if (isDarkMode) currentTheme.darkTerminalColors else currentTheme.lightTerminalColors,
                    )
                    attachSession(session)
                    lastBoundSession = session
                    addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                            applyTerminalColors(
                                onSurfaceColor = colorScheme.onSurface.toArgb(),
                                surfaceColor = colorScheme.surface.toArgb(),
                                terminalColors = if (isDarkMode) currentTheme.darkTerminalColors else currentTheme.lightTerminalColors,
                            )
                        }
                    }
                    post {
                        isFocusable = true; isFocusableInTouchMode = true; requestFocus()
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.restartInput(this)
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
                        view.isFocusable = true; view.isFocusableInTouchMode = true; view.requestFocus()
                        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.restartInput(view)
                    }
                    lastBoundSession = session
                }
                view.attachSession(session)
                view.applyTerminalColors(
                    onSurfaceColor = colorScheme.onSurface.toArgb(),
                    surfaceColor = colorScheme.surface.toArgb(),
                    terminalColors = if (isDarkMode) currentTheme.darkTerminalColors else currentTheme.lightTerminalColors,
                )
            },
        )

        if (showKeys) {
            AndroidView<VirtualKeysView>(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                factory = { context ->
                    VirtualKeysView(context, null).apply {
                        setButtonTextColor(colorScheme.onSurface.toArgb())
                        runCatching {
                            reload(VirtualKeysInfo(Settings.terminal_extra_keys, "", VirtualKeysConstants.CONTROL_CHARS_ALIASES))
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
}
