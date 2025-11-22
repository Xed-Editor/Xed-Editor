package com.rk.lsp.servers

import android.content.Context
import com.rk.exec.TerminalCommand
import com.rk.exec.isTerminalInstalled
import com.rk.exec.launchInternalTerminal
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxHomeDir
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig
import java.net.URI

class Python() : BaseLspServer() {
    override val id: String = "python-lsp"
    override val languageName: String = "Python"
    override val serverName = "python-lsp-server"
    override val supportedExtensions: List<String> = FileType.PYTHON.extensions

    override fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxHomeDir().child(".local/share/pipx/venvs/python-lsp-server/bin/pylsp").exists()
    }

    override fun install(context: Context) {
        val installSH = localBinDir().child("lsp/python")

        launchInternalTerminal(
            context = context,
            terminalCommand =
                TerminalCommand(
                    exe = "/bin/bash",
                    args = arrayOf(installSH.absolutePath),
                    id = "python-lsp-installer",
                    env = arrayOf("DEBIAN_FRONTEND=noninteractive"),
                ),
        )
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/home/.local/share/pipx/venvs/python-lsp-server/bin/pylsp"))
    }

    override fun isSupported(file: FileObject): Boolean {
        return supportedExtensions.contains(file.getName().substringAfterLast("."))
    }

    override fun getInitializationOptions(uri: URI?): Any? {
        return mapOf(
            "plugins" to
                mapOf(
                    "pycodestyle" to
                        mapOf(
                            "enabled" to true,
                            "ignore" to
                                listOf(
                                    "E501", // line too long
                                    "W291", // trailing whitespace
                                    "W293", // blank line contains whitespace
                                    "E301", // expected 1 blank line
                                    "E302", // expected 2 blank lines
                                    "E303", // too many blank lines
                                    "E304", // blank line after function decorator
                                    "W391", // blank line at end of file
                                ),
                            "maxLineLength" to 999,
                        )
                )
        )
    }
}
