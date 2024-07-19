package com.rk.xededitor.terminal

import android.content.Context
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.blankj.utilcode.util.KeyboardUtils
import com.rk.xededitor.rkUtils
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient


class TerminalClient(
  private val terminal: TerminalView, private val terminalActivity: Terminal
) : TerminalViewClient {
  
  
  override fun onScale(scale: Float): Float {
    return TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      scale,
      terminalActivity.getResources().displayMetrics
    )
  }
  
  override fun onSingleTapUp(e: MotionEvent?) {
    terminal.requestFocus()
    KeyboardUtils.showSoftInput()
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
  
  override fun copyModeChanged(copyMode: Boolean) {
  
  }
  
  override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_ENTER && !session?.isRunning!!) {
      terminalActivity.finish()
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
}