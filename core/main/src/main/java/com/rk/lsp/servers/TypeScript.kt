package com.rk.lsp.servers

import android.content.Context
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxHomeDir
import com.rk.exec.TerminalCommand
import com.rk.lsp.BaseLspServer
import com.rk.exec.isTerminalInstalled
import com.rk.exec.launchInternalTerminal

class TypeScript() : BaseLspServer() {
    override val id: String = "typescript-lsp"
    override val languageName: String = "TypeScript"
    override val supportedExtensions: List<String> = listOf("js", "jsx", "ts", "tsx")

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()){
            return false
        }

        return sandboxHomeDir().child("/.npm-global/bin/typescript-language-server").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/typescript")

        launchInternalTerminal(
            context = context,
            terminalCommand = TerminalCommand(
                exe = "/system/bin/sh",
                args = arrayOf(installSH.absolutePath),
                id = "typescript-lsp-installer",
                env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
            )
        )
    }

    override fun command(): Array<String> {
        return arrayOf("/usr/bin/node", "/home/.npm-global/bin/typescript-language-server",  "--stdio")
    }
}