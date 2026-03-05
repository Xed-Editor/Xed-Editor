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

object JSON : ScriptedLspServer() {
    override val id: String = "json"
    override val languageName: String = "JSON"
    override val serverName = "vscode-json-language-server"
    override val supportedExtensions = BuiltinFileType.JSON.extensions
    override val icon = BuiltinFileType.JSON.icon

    override val installScript = localBinDir().child("lsp/json")
    override val installId = "JSON language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/$serverName").exists()
    }

    override suspend fun isUpdatable(context: Context): Boolean {
        return NpmUtils.hasUpdate("vscode-langservers-extracted")
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/$serverName", "--stdio"))
    }
}
