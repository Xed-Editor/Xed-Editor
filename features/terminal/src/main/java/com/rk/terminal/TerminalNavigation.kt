package com.rk.terminal

import android.content.Context
import android.content.Intent
import com.rk.activities.terminal.Terminal

/** Quote a shell argument so it can be safely used in a single-quoted shell word. */
fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/**
 * Navigates the currently visible terminal session to [directory] by typing a `cd` command.
 * Returns true if a session was available and the command was written.
 */
fun navigateCurrentTerminalTo(directory: String): Boolean {
    val session = terminalView.get()?.currentSession ?: return false
    session.write("cd ${shellQuote(directory)}\r")
    return true
}

/**
 * Opens [directory] in the terminal. If a terminal session is already active, it navigates the
 * current session via `cd` instead of creating a new terminal; otherwise it launches a new
 * terminal activity at the given directory.
 */
fun openDirectoryInTerminal(context: Context, directory: String) {
    if (!navigateCurrentTerminalTo(directory)) {
        context.startActivity(
            Intent(context, Terminal::class.java).apply {
                putExtra("cwd", directory)
            }
        )
    }
}
