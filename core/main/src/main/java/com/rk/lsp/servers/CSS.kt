package com.rk.lsp.servers

import android.content.Context
import com.rk.exec.TerminalCommand
import com.rk.exec.isTerminalInstalled
import com.rk.exec.launchInternalTerminal
import com.rk.file.FileType
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxDir
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig

class CSS : BaseLspServer() {
    override val id: String = "css"
    override val languageName: String = "CSS"
    override val serverName = "vscode-css-language-server"
    override val supportedExtensions: List<String> =
        FileType.CSS.extensions + FileType.SCSS.extensions + FileType.LESS.extensions
    override val icon = FileType.CSS.icon

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/vscode-css-language-server").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/css")

        launchInternalTerminal(
            context = context,
            terminalCommand =
                TerminalCommand(
                    exe = "/bin/bash",
                    args = arrayOf(installSH.absolutePath),
                    id = "css-lsp-installer",
                    env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
                ),
        )
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/vscode-css-language-server", "--stdio"))
    }
}
