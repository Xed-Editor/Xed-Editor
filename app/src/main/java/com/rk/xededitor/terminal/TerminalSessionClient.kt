package com.rk.xededitor.terminal

import com.blankj.utilcode.util.ClipboardUtils
import com.rk.xededitor.rkUtils
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import java.lang.Exception

class TerminalSessionClient(
  private val terminal: TerminalView,
  private val terminalActivity: Terminal
) : TerminalSessionClient {
  
  override fun onTextChanged(changedSession: TerminalSession?) {
    terminal.onScreenUpdated()
  }
  
  override fun onTitleChanged(changedSession: TerminalSession?) {}
  
  override fun onSessionFinished(finishedSession: TerminalSession?) {
    //terminalActivity.finish()
  }
  
  override fun onBell(session: TerminalSession?) {
  
  }
  
  override fun onColorsChanged(session: TerminalSession?) {}
  
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
    e?.printStackTrace()
  }
  
  override fun logStackTrace(tag: String?, e: Exception?) {
    e?.printStackTrace()
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
  
}