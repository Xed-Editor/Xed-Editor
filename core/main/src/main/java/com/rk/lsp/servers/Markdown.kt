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

class Markdown() : BaseLspServer() {
    override val id: String = "markdown-lsp"
    override val languageName: String = "Markdown"
    override val serverName = "vscode-langservers-extracted"
    override val supportedExtensions: List<String> = FileType.MARKDOWN.extensions

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()){
            return false
        }

        return sandboxHomeDir().child("/usr/bin/vscode-markdown-language-server").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/markdown")

        launchInternalTerminal(
            context = context,
            terminalCommand = TerminalCommand(
                exe = "/bin/bash",
                args = arrayOf(installSH.absolutePath),
                id = "markdown-lsp-installer",
                env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
            )
        )
    }



    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/home/usr/bin/vscode-markdown-language-server",  "--stdio"))
    }

    override fun isSupported(file: FileObject): Boolean {
        return supportedExtensions.contains(file.getName().substringAfterLast("."))
    }
    override fun getInitializationOptions(uri: URI?): Any? {
        return null
    }
}