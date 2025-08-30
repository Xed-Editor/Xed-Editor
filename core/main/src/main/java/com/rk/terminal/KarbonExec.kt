package com.rk.terminal

import android.content.Context
import android.content.Intent
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.pendingCommand
import com.rk.libcommons.showTerminalNotice
import com.rk.xededitor.ui.activities.main.MainActivity
import com.rk.xededitor.ui.activities.terminal.Terminal


fun launchInternalTerminal(context: Context, terminalCommand: TerminalCommand) {
    showTerminalNotice(activity = MainActivity.instance!!){
        pendingCommand = terminalCommand
        context.startActivity(
            Intent(
                context, Terminal::class.java
            )
        )
    }
}