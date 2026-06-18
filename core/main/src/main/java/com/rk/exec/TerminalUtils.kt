package com.rk.exec

import android.app.Activity
import android.content.Intent
import com.rk.activities.terminal.Terminal
import com.rk.file.child
import com.rk.file.localDir
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.utils.showTerminalNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun isTerminalInstalled(): Boolean {
    val prefix = java.io.File(com.rk.utils.application!!.filesDir, "usr")
    val hasBash = java.io.File(prefix, "bin/bash").exists()
    return localDir().child(".terminal_setup_ok_DO_NOT_REMOVE").exists() && hasBash
}

suspend fun isTerminalWorking(): Boolean =
    withContext(Dispatchers.IO) {
        val process = ubuntuProcess(command = arrayOf("true"))
        return@withContext process.waitFor() == 0
    }

fun launchTerminal(activity: Activity, terminalCommand: TerminalCommand) {
    showTerminalNotice(activity = activity) {
        pendingCommand = terminalCommand
        activity.startActivity(Intent(activity, Terminal::class.java))
    }
}
