package com.rk.libcommons.editor.lspServers

import android.content.Context
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxHomeDir
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.editor.BaseLspServer
import com.rk.terminal.isTerminalInstalled
import com.rk.terminal.launchInternalTerminal

class Bash() : BaseLspServer() {
    override val id: String = "bash-lsp"
    override val languageName: String = "Bash"
    override val supportedExtensions: List<String> = listOf("bash", "sh")

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxHomeDir().child("/.npm-global/bin/bash-language-server").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/bash")

        launchInternalTerminal(
            context = context,
            terminalCommand = TerminalCommand(
                exe = "/system/bin/sh",
                args = arrayOf(installSH.absolutePath),
                id = "bash-lsp-installer",
                env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
            )
        )
    }

    override fun command(): Array<String> {
        return arrayOf("/usr/bin/node", "/home/.npm-global/bin/bash-language-server", "start")
    }
}