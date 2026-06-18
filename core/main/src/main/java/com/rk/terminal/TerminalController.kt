package com.rk.terminal

import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.rk.terminal.virtualkeys.VirtualKeysView
import java.lang.ref.WeakReference

interface TerminalController {
    val sessions: List<TerminalSession>
    val sessionIds: List<String>
    val currentSessionId: String?
    val currentSession: TerminalSession?
    
    fun changeSession(sessionId: String)
    fun createSession(sessionId: String): TerminalSession
    fun terminateSession(sessionId: String)

    var terminalViewRef: WeakReference<TerminalView?>
    var virtualKeysViewRef: WeakReference<VirtualKeysView?>
}

