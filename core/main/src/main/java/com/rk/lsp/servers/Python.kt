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
import com.rk.lsp.BaseLspConnector
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig
import org.eclipse.lsp4j.DidChangeConfigurationParams

class Python() : BaseLspServer() {
    override val id: String = "python"
    override val languageName: String = "Python"
    override val serverName = "python-lsp-server"
    override val supportedExtensions: List<String> = FileType.PYTHON.extensions
    override val icon = FileType.PYTHON.icon

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

    override suspend fun connectionSuccess(lspConnector: BaseLspConnector) {
        val requestManager = lspConnector.lspEditor!!.requestManager!!

        val params =
            DidChangeConfigurationParams(
                mapOf(
                    "pylsp" to
                        mapOf(
                            "plugins" to
                                mapOf(
                                    "pycodestyle" to
                                        mapOf(
                                            "enabled" to true,
                                            "ignore" to listOf("E501", "W291", "W293"),
                                            "maxLineLength" to 999,
                                        )
                                )
                        )
                )
            )

        requestManager.didChangeConfiguration(params)
    }

    override suspend fun connectionFailure(msg: String?) {}

    override fun isSupported(file: FileObject): Boolean {
        return supportedExtensions.contains(file.getName().substringAfterLast("."))
    }
}
