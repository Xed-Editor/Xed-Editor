package com.rk.xededitor.terminal.virtualkeys

import android.view.View
import android.widget.Button
import com.rk.xededitor.terminal.virtualkeys.VirtualKeysView.IVirtualKeysView
import com.termux.terminal.TerminalSession

class VirtualKeyClient(val session: TerminalSession) : IVirtualKeysView {
  override fun onVirtualKeyButtonClick(
    view: View?, buttonInfo: VirtualKeyButton?, button: Button?
  ) {
    val key = buttonInfo?.key
    if (key.isNullOrEmpty()) {
      return
    }
    when (key) {
      "ESC" -> session.write("\u001B")      // ESC
      "TAB" -> session.write("\u0009")      // TAB
      "HOME" -> session.write("\u001B[H")     // HOME
      "UP" -> session.write("\u001B[A")     // UP Arrow (ANSI escape code)
      "DOWN" -> session.write("\u001B[B")   // DOWN Arrow (ANSI escape code)
      "LEFT" -> session.write("\u001B[D")   // LEFT Arrow (ANSI escape code)
      "RIGHT" -> session.write("\u001B[C")  // RIGHT Arrow (ANSI escape code)
      "PGUP" -> session.write("\u001B[5~")  // Page Up (ANSI escape code)
      "PGDN" -> session.write("\u001B[6~")  // Page Down (ANSI escape code)
      "END" -> session.write("\u001B[4~")   // End (ANSI escape code, may vary)
      else -> session.write(buttonInfo.key)
    }
    
    
  }
  
  override fun performVirtualKeyButtonHapticFeedback(
    view: View?, buttonInfo: VirtualKeyButton?, button: Button?
  ): Boolean {
    return false
  }
  
}