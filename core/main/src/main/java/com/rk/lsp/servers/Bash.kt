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

object Bash : ScriptedLspServer() {
    override val id: String = "bash"
    override val languageName: String = "Bash"
    override val serverName = "bash-language-server"
    override val supportedExtensions = BuiltinFileType.SHELL.extensions
    override val icon = BuiltinFileType.SHELL.icon

    override val installScript = localBinDir().child("lsp/bash")
    override val installId = "Bash language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/$serverName").exists()
    }

    override suspend fun isUpdatable(context: Context): Boolean {
        return NpmUtils.hasUpdate(serverName)
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/$serverName", "start"))
    }
}
