package com.rk.libcommons.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.dialog
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.terminal.launchInternalTerminal
import com.rk.xededitor.ui.activities.terminal.Terminal

abstract class LspServer {
    abstract fun isInstalled(context: Context): Boolean
    abstract fun install(context: Context)
    abstract fun command(): Array<String>
    abstract val id: String
}

class PyLspServer : LspServer() {

    override val id: String = "python-lsp"

    override fun isInstalled(context: Context): Boolean {
        if (!sandboxDir().exists()) return false
        return sandboxHomeDir().child(".local/share/pipx/venvs/python-lsp-server/bin/pylsp").exists()
    }

    override fun install(context: Context) {
        if (sandboxDir().exists().not() || sandboxDir().listFiles()?.isEmpty() == true){
            dialog(
                title = strings.err.getString(),
                msg = strings.terminal_not_installed.getString(),
                onOk = {
                    context.startActivity(Intent(context, Terminal::class.java))
                }
            )
            return
        }else{
            launchInternalTerminal(
                context = context,
                terminalCommand = TerminalCommand(exe = "/bin/bash",
                    args = arrayOf("-c",
                        "\"apt update && apt upgrade -y && apt install -y pipx && pipx install 'python-lsp-server[all]' && clear && echo 'Successfully installed close all tabs and reopen.' \""
                    ),
                    id = "python-lsp-installer",
                    env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
                )
            )
        }
    }

    override fun command(): Array<String> {
        return arrayOf("/home/.local/share/pipx/venvs/python-lsp-server/bin/pylsp")
    }


}