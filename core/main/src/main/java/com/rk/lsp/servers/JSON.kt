package com.rk.lsp.servers

import android.content.Context
import com.rk.exec.TerminalCommand
import com.rk.exec.isTerminalInstalled
import com.rk.exec.launchInternalTerminal
import com.rk.file.BuiltinFileType
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxDir
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig

class JSON : BaseLspServer() {
    override val id: String = "json"
    override val languageName: String = "JSON"
    override val serverName = "vscode-json-language-server"
    override val supportedExtensions: List<String> = BuiltinFileType.JSON.extensions
    override val icon = BuiltinFileType.JSON.icon

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/vscode-json-language-server").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/json")

        launchInternalTerminal(
            context = context,
            terminalCommand =
                TerminalCommand(
                    exe = "/bin/bash",
                    args = arrayOf(installSH.absolutePath),
                    id = "json-lsp-installer",
                    env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
                ),
        )
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/vscode-json-language-server", "--stdio"))
    }
}
