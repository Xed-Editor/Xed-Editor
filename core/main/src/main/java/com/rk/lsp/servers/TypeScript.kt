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

class TypeScript : BaseLspServer() {
    override val id: String = "typescript"
    override val languageName: String = "TypeScript"
    override val serverName = "typescript-language-server"
    override val supportedExtensions: List<String> =
        BuiltinFileType.JAVASCRIPT.extensions +
            BuiltinFileType.TYPESCRIPT.extensions +
            BuiltinFileType.JSX.extensions +
            BuiltinFileType.TSX.extensions
    override val icon = BuiltinFileType.TYPESCRIPT.icon

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/typescript-language-server").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/typescript")

        launchInternalTerminal(
            context = context,
            terminalCommand =
                TerminalCommand(
                    exe = "/bin/bash",
                    args = arrayOf(installSH.absolutePath),
                    id = "typescript-lsp-installer",
                    env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
                ),
        )
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/typescript-language-server", "--stdio"))
    }
}
