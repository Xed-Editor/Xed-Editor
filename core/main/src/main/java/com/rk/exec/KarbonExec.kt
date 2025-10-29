package com.rk.exec

import android.content.Context
import android.content.Intent
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.utils.showTerminalNotice
import com.rk.activities.main.MainActivity
import com.rk.activities.terminal.Terminal
import com.rk.file.localDir


fun isTerminalInstalled(): Boolean{
    val rootfs = sandboxDir().listFiles()?.filter {
        it.absolutePath != sandboxHomeDir().absolutePath && it.absolutePath != sandboxDir().child(
            "tmp"
        ).absolutePath
    } ?: emptyList()

    return localDir().child(".terminal_setup_ok_DO_NOT_REMOVE").exists() && rootfs.isNotEmpty()
}

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


