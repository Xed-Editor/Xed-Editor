package com.rk.lsp.servers

import android.content.Context
import com.rk.exec.TerminalCommand
import com.rk.exec.isTerminalInstalled
import com.rk.exec.launchInternalTerminal
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxDir
import com.rk.lsp.BaseLspConnector
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig
import java.net.URI

class CSS() : BaseLspServer() {
    override val id: String = "css-lsp"
    override val languageName: String = "CSS"
    override val serverName = "vscode-langservers-extracted"
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

    override suspend fun beforeConnect() {}

    override suspend fun connectionSuccess(lspConnector: BaseLspConnector) {}

    override suspend fun connectionFailure(msg: String?) {}

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/vscode-css-language-server", "--stdio"))
    }

    override fun isSupported(file: FileObject): Boolean {
        return supportedExtensions.contains(file.getName().substringAfterLast("."))
    }

    override fun getInitializationOptions(uri: URI?): Any? {
        return null
    }
}
