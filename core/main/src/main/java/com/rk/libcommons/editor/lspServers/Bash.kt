package com.rk.libcommons.editor.lspServers

import android.content.Context
import com.rk.libcommons.editor.BaseLspServer
import com.rk.terminal.isTerminalInstalled

class Bash() : BaseLspServer() {
    override val id: String = "bash-lsp"
    override val languageName: String = "Bash"
    override val supportedExtensions: List<String> = listOf()

    override fun isInstalled(context: Context): Boolean {
        if (isTerminalInstalled().not()){
            return false
        }

        return true
    }

    override fun install(context: Context) {
        TODO("Not yet implemented")
    }

    override fun command(): Array<String> {
        TODO("Not yet implemented")
    }
}