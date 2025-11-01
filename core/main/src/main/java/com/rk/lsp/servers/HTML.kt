package com.rk.lsp.servers

import android.content.Context
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxHomeDir
import com.rk.exec.TerminalCommand
import com.rk.lsp.BaseLspServer
import com.rk.exec.isTerminalInstalled
import com.rk.exec.launchInternalTerminal

class HTML() : BaseLspServer() {
    override val id: String = "html-lsp"
    override val languageName: String = "HTML"
    override val supportedExtensions: List<String> = listOf("html", "htm", "xht", "xhtml")

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()){
            return false
        }

        return sandboxHomeDir().child("/.npm-global/bin/vscode-html-language-server").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/html")

        launchInternalTerminal(
            context = context,
            terminalCommand = TerminalCommand(
                exe = "/bin/sh",
                args = arrayOf(installSH.absolutePath),
                id = "html-lsp-installer",
                env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
            )
        )
    }

    override fun command(): Array<String> {
        return arrayOf("/usr/bin/node", "/home/.npm-global/bin/vscode-html-language-server",  "--stdio")
    }
}