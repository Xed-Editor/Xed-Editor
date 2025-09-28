package com.rk.libcommons.editor.lspServers

import android.content.Context
import com.rk.file.child
import com.rk.file.sandboxHomeDir
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.editor.BaseLspServer
import com.rk.terminal.isTerminalInstalled
import com.rk.terminal.launchInternalTerminal

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
        val installCommand = """
            apt update && \
            apt upgrade -y && \
            apt install curl -y && \
            curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - && \
            apt install -y nodejs && \
            mkdir -p /home/.npm-global && \
            npm config set prefix '/home/.npm-global' && \
            grep -qxF 'export PATH="/home/.npm-global/bin:${"$"}PATH"' ~/.bashrc || \
                echo 'export PATH="/home/.npm-global/bin:${"$"}PATH"' >> ~/.bashrc && \
            export PATH="/home/.npm-global/bin:${"$"}PATH" && \
            npm install -g typescript typescript-language-server && \
            clear && \
            echo 'TypeScript language server installed successfully. Please reopen all tabs or restart the app.'
        """.trimIndent()

        launchInternalTerminal(
            context = context,
            terminalCommand = TerminalCommand(
                exe = "/bin/bash",
                args = arrayOf("-c", "\"${installCommand}\""),
                id = "typescript-lsp-installer",
                env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
            )
        )
    }

    override fun command(): Array<String> {
        return arrayOf("/usr/bin/node", "/home/.npm-global/bin/typescript-language-server",  "--stdio")
    }
}