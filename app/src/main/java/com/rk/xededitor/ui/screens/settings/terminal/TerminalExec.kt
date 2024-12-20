package com.rk.xededitor.ui.screens.settings.terminal

import android.content.Intent
import com.rk.libcommons.localBinDir
import com.rk.xededitor.ui.activities.settings.Terminal
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

object TerminalExec {
    fun getSession(
        context: Terminal, intent: Intent, client: TerminalSessionClient, env: Array<String>
    ): TerminalSession {
        val shell = intent.getStringExtra("shell")!!
        val Xargs = intent.getStringArrayExtra("args")!!
        val sessionId = intent.getStringExtra("session_id")!!
        val terminatePreviousSession = intent.getBooleanExtra("terminate_prev", true)
        val alpine = intent.getBooleanExtra("alpine", true)
        val cwd = intent.getStringExtra("cwd")!!
        val Xenv = intent.getStringArrayExtra("env")!!


        if (terminatePreviousSession) {
            context.sessionBinder?.terminateSession(sessionId)
        }


        val args = if (alpine.not()) {
            Xargs
        } else {
            val initHost = context.localBinDir().child("init-host")
            if (initHost.exists().not()) {
                initHost.createFileIfNot()
                initHost.writeText(context.assets.open("terminal/init-host.sh").bufferedReader()
                    .use { it.readText() })
            }

            val init = context.localBinDir().child("init")
            if (init.exists().not()) {
                init.createFileIfNot()
                init.writeText(context.assets.open("terminal/init.sh").bufferedReader()
                    .use { it.readText() })
            }

            arrayOf("-c", initHost.absolutePath, *Xargs)
        }

        intent.removeExtra("run_cmd")
        val Yenv = env.toMutableList();
        Yenv.addAll(Xenv)

        return TerminalSession(
            shell,
            cwd,
            args,
            Yenv.toTypedArray(),
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            client,
        )
    }
}