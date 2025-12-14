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

class Bash() : BaseLspServer() {
    override val id: String = "bash-lsp"
    override val languageName: String = "Bash"
    override val serverName = "bash-language-server"
    override val supportedExtensions: List<String> = FileType.SHELL.extensions
    override val icon = FileType.SHELL.icon

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/bash-language-server").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/bash")

        launchInternalTerminal(
            context = context,
            terminalCommand =
                TerminalCommand(
                    exe = "/bin/bash",
                    args = arrayOf(installSH.absolutePath),
                    id = "bash-lsp-installer",
                    env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
                ),
        )
    }

    override suspend fun beforeConnect() {}

    override suspend fun connectionSuccess(lspConnector: BaseLspConnector) {}

    override suspend fun connectionFailure(msg: String?) {}

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/bash-language-server", "start"))
    }

    override fun getInitializationOptions(uri: URI?): Any? {
        return null
    }

    override fun isSupported(file: FileObject): Boolean {
        return supportedExtensions.contains(file.getName().substringAfterLast("."))
    }
}
