package com.rk

import android.app.Activity

object TerminalLauncher {
    // A delegate function to launch the terminal activity dynamically
    var handler: ((
        activity: Activity,
        sandbox: Boolean,
        exe: String,
        args: Array<String>,
        id: String,
        terminatePreviousSession: Boolean,
        workingDir: String?,
        env: Array<String>
    ) -> Unit)? = null

    fun launch(
        activity: Activity,
        sandbox: Boolean = true,
        exe: String,
        args: Array<String> = arrayOf(),
        id: String,
        terminatePreviousSession: Boolean = true,
        workingDir: String? = null,
        env: Array<String> = arrayOf()
    ) {
        handler?.invoke(activity, sandbox, exe, args, id, terminatePreviousSession, workingDir, env)
    }
}

