package com.rk.activities.terminal

import android.app.Activity
import android.content.Context
import android.content.Intent

object TerminalNavigation {
    private var terminalClass: Class<*>? = null

    fun setTerminalClass(clazz: Class<*>) {
        terminalClass = clazz
    }

    fun getTerminalClass(): Class<*> {
        return terminalClass ?: throw IllegalStateException("Terminal class not registered")
    }

    fun startTerminal(context: Context, cwd: String? = null, setupIntent: (Intent.() -> Unit)? = null) {
        val intent = Intent(context, getTerminalClass())
        if (cwd != null) {
            intent.putExtra("cwd", cwd)
        }
        setupIntent?.invoke(intent)
        context.startActivity(intent)
    }
}
