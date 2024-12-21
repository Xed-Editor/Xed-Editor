package com.rk.xededitor.ui.screens.settings.terminal

import android.app.Activity
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.activities.settings.Terminal
import com.rk.xededitor.ui.screens.settings.terminal.virtualkeys.SpecialButton
import com.rk.xededitor.ui.screens.settings.terminal.virtualkeys.VirtualKeysView
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class TerminalBackEnd(val terminal: TerminalView,val activity:Terminal) : TerminalViewClient, TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {
        terminal.onScreenUpdated()
    }
    
    override fun onTitleChanged(changedSession: TerminalSession) {}
    
    override fun onSessionFinished(finishedSession: TerminalSession) {

    }
    
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ClipboardUtils.copyText("Terminal", text)
    }
    
    override fun onPasteTextFromClipboard(session: TerminalSession) {
        val clip = ClipboardUtils.getText().toString()
        if (clip.trim { it <= ' ' }.isNotEmpty() && terminal.mEmulator != null) {
            terminal.mEmulator.paste(clip)
        }
    }
    
    override fun onBell(session: TerminalSession) {

    }
    
    override fun onColorsChanged(session: TerminalSession) {}
    
    override fun onTerminalCursorStateChange(state: Boolean) {}
    
    override fun getTerminalCursorStyle(): Int {
        return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
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
        return rkUtils.dpToPx(14f,terminal.context).toFloat()
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
            activity.sessionBinder?.terminateSession(activity.sessionBinder!!.getService().currentSession.value)
            if (activity.sessionBinder!!.getService().sessionList.isEmpty()){
                activity.finish()
            }else{
                terminal?.apply {
                    val id = activity.sessionBinder!!.getService().sessionList.first()
                    activity.sessionBinder!!.getService().currentSession.value = id
                    val client = TerminalBackEnd(this, activity)
                    val session =
                        activity.sessionBinder!!.getSession(id)
                            ?: activity.sessionBinder!!.createSession(
                                id,
                                client,
                                activity
                            )
                    session.updateTerminalSessionClient(client)
                    attachSession(session)
                    setTerminalViewClient(client)
                    post {
                        val typedValue = TypedValue()

                        context.theme.resolveAttribute(
                            com.google.android.material.R.attr.colorOnSurface,
                            typedValue,
                            true
                        )
                        keepScreenOn = true
                        requestFocus()
                        setFocusableInTouchMode(true)

                        mEmulator?.mColors?.mCurrentColors?.apply {
                            set(256, typedValue.data)
                            set(258, typedValue.data)
                        }
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
        val state = activity.findViewById<VirtualKeysView>(virtualKeysId).readSpecialButton(SpecialButton.CTRL, true)
        return state != null && state
    }
    
    override fun readAltKey(): Boolean {
       val state = activity.findViewById<VirtualKeysView>(virtualKeysId).readSpecialButton(SpecialButton.ALT, true)
        return state != null && state
    }
    
    override fun readShiftKey(): Boolean {
        val state = activity.findViewById<VirtualKeysView>(virtualKeysId).readSpecialButton(SpecialButton.SHIFT, true)
        return state != null && state
    }
    
    override fun readFnKey(): Boolean {
        val state = activity.findViewById<VirtualKeysView>(virtualKeysId).readSpecialButton(SpecialButton.FN, true)
        return state != null && state
    }
    
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        return false
    }
    
    override fun onEmulatorSet() {
        setTerminalCursorBlinkingState(true)
    }
    
    private fun setTerminalCursorBlinkingState(start: Boolean) {
        if (terminal.mEmulator != null) {
            terminal.setTerminalCursorBlinkerState(start, true)
        }
    }
    
    private fun showSoftInput() {
        terminal.requestFocus()
        KeyboardUtils.showSoftInput(terminal)
    }
}
