package com.rk.xededitor.terminal.virtualkeys

import android.view.View
import android.widget.Button
import com.termux.terminal.TerminalSession

class VirtualKeysListener(val session: TerminalSession) : VirtualKeysView.IVirtualKeysView {
  
  override fun onVirtualKeyButtonClick(
    view: View?, buttonInfo: VirtualKeyButton?, button: Button?
  ) {
    
    
    val key = buttonInfo?.key ?: return
    val writeable: String = when (key) {
      "UP" -> "\u001B[A"  // Escape sequence for Up Arrow
      "DOWN" -> "\u001B[B"  // Escape sequence for Down Arrow
      "LEFT" -> "\u001B[D"  // Escape sequence for Left Arrow
      "RIGHT" -> "\u001B[C"  // Escape sequence for Right Arrow
      "ENTER" -> "\u000D"  // Carriage Return for Enter
      "PGUP" -> "\u001B[5~"  // Escape sequence for Page Up
      "PGDN" -> "\u001B[6~"  // Escape sequence for Page Down
      "TAB" -> "\u0009"  // Horizontal Tab
      "HOME" -> "\u001B[H"  // Escape sequence for Home
      "END" -> "\u001B[F"  // Escape sequence for End
      "ESC" -> "\u001B"  // Escape
      else -> key
    }
    
    session.write(writeable)
    
    
    
    
  }
  
  override fun performVirtualKeyButtonHapticFeedback(
    view: View?, buttonInfo: VirtualKeyButton?, button: Button?
  ): Boolean {
    return false
  }
}