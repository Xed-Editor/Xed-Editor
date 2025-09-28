package com.rk.libcommons.editor.lspServers

import android.content.Context
import com.rk.file.child
import com.rk.file.sandboxHomeDir
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.editor.BaseLspServer
import com.rk.terminal.isTerminalInstalled
import com.rk.terminal.launchInternalTerminal

class Python() : BaseLspServer() {
    override val id: String = "python-lsp"
    override val languageName: String = "Python"
    override val supportedExtensions: List<String> = listOf("py")

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxHomeDir().child(".local/share/pipx/venvs/python-lsp-server/bin/pylsp").exists()
    }

    override fun install(context: Context) {
        val installCommand = """
            apt update && \
            apt upgrade -y && \
            apt install -y pipx && \
            pipx ensurepath && \
            pipx install 'python-lsp-server[all]' && \
            clear && \
            echo 'Python language server installed successfully. Please reopen all tabs or restart the app.'
        """.trimIndent()

        launchInternalTerminal(
            context = context,
            terminalCommand = TerminalCommand(
                exe = "/bin/bash",
                args = arrayOf("-c", "\"${installCommand}\""),
                id = "python-lsp-installer",
                env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
            )
        )
    }

    override fun command(): Array<String> {
        return arrayOf("/home/.local/share/pipx/venvs/python-lsp-server/bin/pylsp")
    }
}