package com.rk.lsp.servers

import android.content.Context
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxHomeDir
import com.rk.exec.TerminalCommand
import com.rk.lsp.BaseLspServer
import com.rk.exec.isTerminalInstalled
import com.rk.exec.launchInternalTerminal
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.lsp.LspConnectionConfig
import java.net.URI

class Emmet() : BaseLspServer() {
    override val id: String = "emmet-lsp"
    override val languageName: String = "Emmet"
    override val serverName = "emmet-language-server"
    override val supportedExtensions: List<String> = FileType.HTML.extensions + FileType.HTMX.extensions

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()){
            return false
        }

        return sandboxHomeDir().child("/.npm-global/bin/emmet-language-server").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/emmet")

        launchInternalTerminal(
            context = context,
            terminalCommand = TerminalCommand(
                exe = "/bin/bash",
                args = arrayOf(installSH.absolutePath),
                id = "emmet-lsp-installer",
                env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
            )
        )
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/home/.npm-global/bin/emmet-language-server",  "--stdio"))
    }

    override fun isSupported(file: FileObject): Boolean {
        return supportedExtensions.contains(file.getName().substringAfterLast("."))
    }

    override fun getInitializationOptions(uri: URI?): Any? {
        return null
    }
}