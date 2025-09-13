package com.rk.libcommons.editor.lspServers

import android.content.Context
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.editor.BaseLspServer
import com.rk.terminal.isTerminalInstalled
import com.rk.terminal.launchInternalTerminal

class Python() : BaseLspServer() {
    override val id: String = "python-lsp"
    override val languageName: String = "Python"



    override fun isInstalled(context: Context): Boolean {
        if (isTerminalInstalled().not()){
            return false
        }

        return sandboxDir().child("bin/pylsp").exists()
    }

    override fun install(context: Context) {
        launchInternalTerminal(
            context = context,
            terminalCommand = TerminalCommand(exe = "/bin/bash",
                args = arrayOf("-c",
                    "\"apt update -y && apt install python3-pylsp -y && clear && echo 'Successfully installed close all tabs and reopen.' \""
                ),
                id = "python-lsp-installer",
                env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
            )
        )
    }

    override fun command(): Array<String> {
        return arrayOf("/bin/pylsp")
    }
}