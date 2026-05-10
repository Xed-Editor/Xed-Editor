package com.rk.tabs.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalSession

object GeminiSheetSessionStore {
    var session by mutableStateOf<TerminalSession?>(null)
    var cwd by mutableStateOf<String?>(null)
    private val allowRootReuse: Boolean =
        (System.getenv("XED_GEMINI_REUSE_ROOT") ?: "true").equals("true", ignoreCase = true)

    fun canReuseFor(requestedCwd: String): Boolean {
        val running = session
        val existingCwd = cwd?.trimEnd('/') ?: return false
        val requested = requestedCwd.trimEnd('/')
        if (running?.isRunning != true) return false
        if (existingCwd == requested) return true
        if (!allowRootReuse && (existingCwd == "/home" || existingCwd == "/storage/emulated/0" || existingCwd == "/")) return false
        return requested.startsWith("$existingCwd/")
    }

    fun stop() {
        session?.finishIfRunning()
        session = null
        cwd = null
    }
}
