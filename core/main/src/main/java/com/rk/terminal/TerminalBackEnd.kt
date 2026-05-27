package com.rk.terminal

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.KeyboardUtils
import android.app.Activity
import com.rk.settings.Settings
import com.rk.settings.terminal.TerminalCursorStyle
import com.rk.terminal.virtualkeys.SpecialButton
import com.rk.terminal.changeTerminalSession
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient
import java.lang.ref.WeakReference

class TerminalBackEnd(terminalViewModel: TerminalViewModel? = null) : TerminalViewClient, TerminalSessionClient {
    private val viewModelRef = WeakReference(terminalViewModel)
    private val terminalViewModel: TerminalViewModel?
        get() = viewModelRef.get()

    override fun onTextChanged(changedSession: TerminalSession) {
        terminalViewModel?.terminalView?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ClipboardUtils.copyText("Terminal", text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = ClipboardUtils.getText().toString()
        if (clip.trim { it <= ' ' }.isNotEmpty() && terminalViewModel?.terminalView?.mEmulator != null) {
            terminalViewModel?.terminalView?.mEmulator?.paste(clip)
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

    override fun logError(tag: String?, message: String?) {
        Log.e(tag.toString(), message.toString())
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag.toString(), message.toString())
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag.toString(), message.toString())
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag.toString(), message.toString())
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag.toString(), message.toString())
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag.toString(), message.toString())
        e?.printStackTrace()
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        e?.printStackTrace()
    }

    override fun onScale(scale: Float): Float {
        val fontScale = scale.coerceIn(11f, 45f)
        terminalViewModel?.terminalView?.setTextSize(fontScale.toInt())
        return fontScale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        showSoftInput()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean {
        return false
    }

    override fun shouldEnforceCharBasedInput(): Boolean {
        return true
    }

    override fun shouldUseCtrlSpaceWorkaround(): Boolean {
        return true
    }

    override fun isTerminalViewSelected(): Boolean {
        return true
    }

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            val context = terminalViewModel?.terminalView?.context ?: return false
            val binder = terminalViewModel?.sessionBinder ?: return false
            
            // Find the correct session ID for this session
            val sessionId = binder.getService()?.sessionList?.find { 
                binder.getSession(it) == session 
            } ?: binder.getService()?.currentSession?.value ?: ""
            
            binder.terminateSession(sessionId)
            
            if (binder.getService()?.sessionList?.isEmpty() == true) {
                (context as? Activity)?.finish()
            } else {
                val firstSession = binder.getService()?.sessionList?.firstOrNull() ?: return false
                (context as? Activity)?.let { activity ->
                    com.rk.AppScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        changeTerminalSession(firstSession, terminalViewModel!!, activity)
                    }
                }
            }
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        return false
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        return false
    }

    // keys
    override fun readControlKey(): Boolean {
        val state = terminalViewModel?.virtualKeysView?.readSpecialButton(SpecialButton.CTRL, true)
        return state != null && state
    }

    override fun readAltKey(): Boolean {
        val state = terminalViewModel?.virtualKeysView?.readSpecialButton(SpecialButton.ALT, true)
        return state != null && state
    }

    override fun readShiftKey(): Boolean {
        val state = terminalViewModel?.virtualKeysView?.readSpecialButton(SpecialButton.SHIFT, true)
        return state != null && state
    }

    override fun readFnKey(): Boolean {
        val state = terminalViewModel?.virtualKeysView?.readSpecialButton(SpecialButton.FN, true)
        return state != null && state
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        return false
    }

    override fun onEmulatorSet() {
        setTerminalCursorBlinkingState(true)
    }

    private fun setTerminalCursorBlinkingState(start: Boolean) {
        if (terminalViewModel?.terminalView?.mEmulator != null) {
            terminalViewModel?.terminalView?.setTerminalCursorBlinkerState(start, true)
        }
    }

    private fun showSoftInput() {
        val view = terminalViewModel?.terminalView ?: return
        view.post {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
            val inputMethodManager =
                view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputMethodManager?.restartInput(view)
            inputMethodManager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            KeyboardUtils.showSoftInput(view)
        }
    }
}
