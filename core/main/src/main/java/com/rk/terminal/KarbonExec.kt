package com.rk.terminal

import android.content.Context
import android.content.Intent
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.pendingCommand
import com.rk.xededitor.ui.activities.terminal.Terminal


fun launchInternalTerminal(context: Context, terminalCommand: TerminalCommand) {
    pendingCommand = terminalCommand
    context.startActivity(
        Intent(
            context, Terminal::class.java
        )
    )
}