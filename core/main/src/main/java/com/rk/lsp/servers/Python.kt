package com.rk.lsp.servers

import android.content.Context
import com.rk.exec.NpmUtils
import com.rk.exec.isTerminalInstalled
import com.rk.file.BuiltinFileType
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxDir
import com.rk.lsp.LspConnectionConfig
import com.rk.lsp.ScriptedLspServer

object Python : ScriptedLspServer() {
    override val id: String = "python"
    override val languageName: String = "Python"
    override val serverName = "pyright"
    override val supportedExtensions = BuiltinFileType.PYTHON.extensions
    override val icon = BuiltinFileType.PYTHON.icon

    override val installScript = localBinDir().child("lsp/python")
    override val installId = "Pyright language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/pyright-langserver").exists()
    }

    override suspend fun isUpdatable(context: Context): Boolean {
        return NpmUtils.hasUpdate("pyright")
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/pyright-langserver", "--stdio"))
    }
}
