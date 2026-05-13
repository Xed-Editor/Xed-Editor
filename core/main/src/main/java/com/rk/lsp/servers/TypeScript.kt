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

object TypeScript : ScriptedLspServer() {
    override val id: String = "typescript"
    override val languageName: String = "TypeScript"
    override val serverName = "typescript-language-server"
    override val supportedExtensions =
        BuiltinFileType.JAVASCRIPT.extensions +
            BuiltinFileType.TYPESCRIPT.extensions +
            BuiltinFileType.JSX.extensions +
            BuiltinFileType.TSX.extensions
    override val icon = BuiltinFileType.TYPESCRIPT.icon

    override val installScript = localBinDir().child("lsp/typescript")
    override val installId = "TypeScript language server"

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
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/$serverName", "--stdio"))
    }
}
