package com.rk.xededitor.MainActivity.tabs.terminal

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.SizeUtils
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.ProjectManager
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import java.lang.Exception

class TerminalFragment(val context: Context) : CoreFragment, TerminalViewClient, TerminalSessionClient {
    private val terminal = TerminalView(context,null)
    override fun getView(): View? {
        return terminal
    }
    
    override fun onDestroy() {
        terminal.mTermSession?.finishIfRunning()
    }
    
    override fun onClosed() {
        onDestroy()
    }
    
    override fun onCreate() {
        terminal.setTerminalViewClient(this)
        val session = createSession()
        terminal.attachSession(session)
        terminal.setBackgroundColor(Color.BLACK)
        terminal.setTextSize(
            SizeUtils.dp2px(
                PreferencesData.getString(PreferencesKeys.TERMINAL_TEXT_SIZE, "14").toFloat()
            )
        )
        terminal.requestFocus()
        terminal.setFocusableInTouchMode(true)
    }
    
    private fun createSession(): TerminalSession {
        val workingDir = ProjectManager.currentProject.get(MainActivity.activityRef.get()!!).absolutePath
        
        val tmpDir = File(context.getTempDir(), "terminal")
        
        val env =
            arrayOf(
                "PROOT_TMP_DIR=${tmpDir.absolutePath}",
                "HOME=" + context.filesDir.absolutePath,
                "PUBLIC_HOME=" + context.getExternalFilesDir(null)?.absolutePath,
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
            )
        
        
        val shell = "/system/bin/sh"
        val args =
            if (PreferencesData.getBoolean(PreferencesKeys.FAIL_SAFE, false)) {
                arrayOf("")
            } else {
                arrayOf("-c", File(context.filesDir.parentFile!!, "proot.sh").absolutePath)
            }
        
        return TerminalSession(
            shell,
            workingDir,
            args,
            env,
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            this,
        )
    }
    
    override fun loadFile(file: File) {}
    
    override fun getFile(): File? {
        return null
    }
    
    private var fontSize =
        SizeUtils.dp2px(
            PreferencesData.getString(PreferencesKeys.TERMINAL_TEXT_SIZE, "14").toFloat()
        )
    override fun onScale(scale: Float): Float {
        return fontSize.toFloat()
    }
    
    override fun onSingleTapUp(e: MotionEvent?) {
        KeyboardUtils.showSoftInput(terminal)
    }
    
    override fun shouldBackButtonBeMappedToEscape(): Boolean {
        return false
    }
    
    override fun shouldEnforceCharBasedInput(): Boolean {
        return PreferencesData.getBoolean(PreferencesKeys.FORCE_CHAR, true)
    }
    
    override fun shouldUseCtrlSpaceWorkaround(): Boolean {
        return PreferencesData.getBoolean(PreferencesKeys.CTRL_WORKAROUND, false)
    }
    
    override fun isTerminalViewSelected(): Boolean {
        return true
    }
    
    override fun copyModeChanged(copyMode: Boolean) {
        
    }
    
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && session?.isRunning != true) {
            //close tab
            return true
        }
        return false
    }
    
    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean {
        return false
    }
    
    override fun onLongPress(event: MotionEvent?): Boolean {
        return false
    }
    
    override fun readControlKey(): Boolean {
        return false
    }
    
    override fun readAltKey(): Boolean {
        return false
    }
    
    override fun readShiftKey(): Boolean {
        return false
    }
    
    override fun readFnKey(): Boolean {
        return false
    }
    
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
        return false
    }
    
    override fun onEmulatorSet() {
        if (terminal.mEmulator != null) {
            terminal.setTerminalCursorBlinkerState(true, true)
        }
    }
    
    override fun onTextChanged(changedSession: TerminalSession?) {
        terminal.onScreenUpdated()
    }
    
    override fun onTitleChanged(changedSession: TerminalSession?) {
        
    }
    
    override fun onSessionFinished(finishedSession: TerminalSession?) {
        
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
    
    override fun onBell(session: TerminalSession?) {
        
    }
    
    override fun onColorsChanged(session: TerminalSession?) {
        
    }
    
    override fun onTerminalCursorStateChange(state: Boolean) {
        
    }
    
    override fun getTerminalCursorStyle(): Int {
        return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
    }
    
    override fun logError(tag: String?, message: String?) {
        if (message != null) {
            Log.e(tag,message)
        }
    }
    
    override fun logWarn(tag: String?, message: String?) {
        if (message != null) {
            Log.w(tag,message)
        }
    }
    
    override fun logInfo(tag: String?, message: String?) {
        if (message != null) {
            Log.i(tag,message)
        }
    }
    
    override fun logDebug(tag: String?, message: String?) {
        if (message != null) {
            Log.d(tag,message)
        }
    }
    
    override fun logVerbose(tag: String?, message: String?) {
        if (message != null) {
            Log.v(tag,message)
        }
    }
    
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        if (message != null) {
            Log.e(tag,message)
        }
        e?.printStackTrace()
    }
    
    override fun logStackTrace(tag: String?, e: Exception?) {
        e?.printStackTrace()
    }
}
