package com.rk.lsp.servers

import android.content.Context
import com.rk.exec.PipxUtils
import com.rk.exec.isTerminalInstalled
import com.rk.file.BuiltinFileType
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxHomeDir
import com.rk.lsp.LspConnectionConfig
import com.rk.lsp.LspConnector
import com.rk.lsp.ScriptedLspServer
import org.eclipse.lsp4j.DidChangeConfigurationParams

object Python : ScriptedLspServer() {
    override val id: String = "python"
    override val languageName: String = "Python"
    override val serverName = "python-lsp-server"
    override val supportedExtensions = BuiltinFileType.PYTHON.extensions
    override val icon = BuiltinFileType.PYTHON.icon

    override val installScript = localBinDir().child("lsp/python")
    override val installId = "Python language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxHomeDir().child(".local/share/pipx/venvs/python-lsp-server/bin/pylsp").exists()
    }

    override suspend fun isUpdatable(context: Context): Boolean {
        return PipxUtils.hasUpdate(serverName)
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/home/.local/share/pipx/venvs/python-lsp-server/bin/pylsp"))
    }

    override suspend fun onInitialize(lspConnector: LspConnector) {
        val requestManager = lspConnector.lspEditor!!.requestManager

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
}
