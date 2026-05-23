package com.rk.ai

import com.termux.terminal.TerminalSession

object ActiveSession {
    @Volatile var session: TerminalSession? = null
}
