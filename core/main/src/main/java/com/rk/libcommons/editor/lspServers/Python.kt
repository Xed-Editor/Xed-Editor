package com.rk.libcommons.editor.lspServers

import android.content.Context
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxHomeDir
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.editor.BaseLspServer
import com.rk.terminal.isTerminalInstalled
import com.rk.terminal.launchInternalTerminal

class Python() : BaseLspServer() {
    override val id: String = "python-lsp"
    override val languageName: String = "Python"
    override val supportedExtensions: List<String> = listOf("py")

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxHomeDir().child(".local/share/pipx/venvs/python-lsp-server/bin/pylsp").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/python")

        launchInternalTerminal(
            context = context,
            terminalCommand = TerminalCommand(
                exe = "/system/bin/sh",
                args = arrayOf(installSH.absolutePath),
                id = "python-lsp-installer",
                env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
            )
        )
    }

    override fun command(): Array<String> {
        return arrayOf("/home/.local/share/pipx/venvs/python-lsp-server/bin/pylsp")
    }
}