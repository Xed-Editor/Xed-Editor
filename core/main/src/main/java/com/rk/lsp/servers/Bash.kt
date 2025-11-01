package com.rk.lsp.servers

import android.content.Context
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxHomeDir
import com.rk.exec.TerminalCommand
import com.rk.lsp.BaseLspServer
import com.rk.exec.isTerminalInstalled
import com.rk.exec.launchInternalTerminal
import com.rk.lsp.LspConnectionConfig

class Bash() : BaseLspServer() {
    override val id: String = "bash-lsp"
    override val languageName: String = "Bash"
    override val supportedExtensions: List<String> = listOf("bash", "sh","bashrc")

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
                exe = "/bin/bash",
                args = arrayOf(installSH.absolutePath),
                id = "bash-lsp-installer",
                env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
            )
        )
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/home/.npm-global/bin/bash-language-server", "start"))
    }

}