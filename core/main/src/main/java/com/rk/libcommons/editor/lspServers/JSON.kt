package com.rk.libcommons.editor.lspServers

import android.content.Context
import com.rk.file.child
import com.rk.file.sandboxHomeDir
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.editor.BaseLspServer
import com.rk.terminal.isTerminalInstalled
import com.rk.terminal.launchInternalTerminal

class JSON() : BaseLspServer() {
    override val id: String = "json-lsp"
    override val languageName: String = "JSON"
    override val supportedExtensions: List<String> = listOf("json")

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()){
            return false
        }

        return sandboxHomeDir().child("/.npm-global/bin/vscode-json-language-server").exists()
    }

    override suspend fun install(context: Context) {
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
            npm install -g vscode-langservers-extracted && \
            clear && \
            echo 'JSON language server installed successfully. Please reopen all tabs or restart the app.'
        """.trimIndent()

        launchInternalTerminal(
            context = context,
            terminalCommand = TerminalCommand(
                exe = "/bin/bash",
                args = arrayOf("-c", "\"${installCommand}\""),
                id = "json-lsp-installer",
                env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
            )
        )
    }

    override suspend fun command(): Array<String> {
        return arrayOf("/usr/bin/node", "/home/.npm-global/bin/vscode-json-language-server",  "--stdio")
    }
}