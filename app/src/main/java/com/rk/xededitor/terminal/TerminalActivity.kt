package com.rk.xededitor.terminal

import com.rk.xededitor.BaseActivity
import com.rk.xededitor.databinding.ActivityTerminalBinding

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat.getInsetsController
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.SizeUtils
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

class TerminalActivity : BaseActivity(), TerminalViewClient, TerminalSessionClient {


    private var fontSize = SizeUtils.dp2px(14f)
    private lateinit var terminal: TerminalView

    private val workingDirectory: String
        get() {
            val extras = intent.extras
            if (extras != null && extras.containsKey(KEY_WORKING_DIRECTORY)) {
                val directory = extras.getString(KEY_WORKING_DIRECTORY, null)
                return if (directory != null && directory.trim { it <= ' ' }.isNotEmpty()) directory
                else PathUtils.getRootPathExternalFirst()
            }
            return PathUtils.getRootPathExternalFirst()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        window?.also {
            val controller = getInsetsController(it, it.decorView)
            controller.setAppearanceLightNavigationBars(false)
            controller.setAppearanceLightStatusBars(false)
        }
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    terminal.mTermSession.finishIfRunning()
                    finish()
                }
            },
        )

        setupTerminalView()

    }

    override fun onResume() {
        super.onResume()
        showSoftInput()
        setTerminalCursorBlinkingState(true)
    }

    override fun onStop() {
        super.onStop()
        setTerminalCursorBlinkingState(false)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun setupTerminalView() {
        terminal = TerminalView(this, null)
        terminal.setTerminalViewClient(this)
        terminal.attachSession(createSession())
        terminal.keepScreenOn = true
        terminal.setTextSize(fontSize)
        val params = LinearLayout.LayoutParams(-1, 0)
        params.weight = 1f
        //binding.root.addView(terminal, 0, params)
        setContentView(terminal)

    }

    private fun createSession(): TerminalSession {
        var shell = "/bin/sh"
        if (File("/bin/sh").exists().not()) {
            shell = "/system/bin/sh"
        }
        return TerminalSession(
            shell,
            workingDirectory,
            arrayOf(),
            arrayOf(),
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            this,
        )
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        terminal.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {
        finish()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ClipboardUtils.copyText("VCSpace Terminal", text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        val clip = ClipboardUtils.getText().toString()
        if (clip.trim { it <= ' ' }.isNotEmpty() && terminal.mEmulator != null) {
            terminal.mEmulator.paste(clip)
        }
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun getTerminalCursorStyle(): Int {
        return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
    }

    override fun logError(tag: String?, message: String?) {

    }

    override fun logWarn(tag: String?, message: String?) {

    }

    override fun logInfo(tag: String?, message: String?) {

    }

    override fun logDebug(tag: String?, message: String?) {

    }

    override fun logVerbose(tag: String?, message: String?) {

    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {

    }

    override fun logStackTrace(tag: String?, e: Exception?) {

    }

    override fun onScale(scale: Float): Float {
        return fontSize.toFloat()
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
        return false
    }

    override fun isTerminalViewSelected(): Boolean {
        return true
    }

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            finish()
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

    override fun readControlKey(): Boolean {
//        val state = binding.virtualKeys.readSpecialButton(SpecialButton.CTRL, true)
//        return state != null && state
        return false
    }

    override fun readAltKey(): Boolean {
//        val state = binding.virtualKeys.readSpecialButton(SpecialButton.ALT, true)
//        return state != null && state
        return false
    }

    override fun readShiftKey(): Boolean {
        return false
    }

    override fun readFnKey(): Boolean {
        return false
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        return false
    }

    override fun onEmulatorSet() {
        setTerminalCursorBlinkingState(true)
//        binding.root.setBackgroundColor(
//            terminal.mTermSession.emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]
//        )
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



    companion object {
        const val KEY_WORKING_DIRECTORY = "terminal_workingDirectory"
        const val KEY_PYTHON_FILE_PATH = "terminal_python_file"

        const val VIRTUAL_KEYS =
            ("[" +
                    "\n  [" +
                    "\n    \"ESC\"," +
                    "\n    {" +
                    "\n      \"key\": \"/\"," +
                    "\n      \"popup\": \"\\\\\"" +
                    "\n    }," +
                    "\n    {" +
                    "\n      \"key\": \"-\"," +
                    "\n      \"popup\": \"|\"" +
                    "\n    }," +
                    "\n    \"HOME\"," +
                    "\n    \"UP\"," +
                    "\n    \"END\"," +
                    "\n    \"PGUP\"" +
                    "\n  ]," +
                    "\n  [" +
                    "\n    \"TAB\"," +
                    "\n    \"CTRL\"," +
                    "\n    \"ALT\"," +
                    "\n    \"LEFT\"," +
                    "\n    \"DOWN\"," +
                    "\n    \"RIGHT\"," +
                    "\n    \"PGDN\"" +
                    "\n  ]" +
                    "\n]")
    }
}