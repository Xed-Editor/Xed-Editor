package com.rk.lsp.servers

import android.content.Context
import com.rk.exec.isTerminalInstalled
import com.rk.file.BuiltinFileType
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxDir
import com.rk.lsp.LspConnectionConfig
import com.rk.lsp.ScriptedLspServer

object Kotlin : ScriptedLspServer() {
    override val id: String = "kotlin"
    override val languageName: String = "Kotlin"
    override val serverName = "kotlin-language-server"
    override val supportedExtensions = BuiltinFileType.KOTLIN.extensions
    override val icon = BuiltinFileType.KOTLIN.icon

    override val installScript = localBinDir().child("lsp/kotlin")
    override val installId = "Kotlin language server"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxDir().child("/usr/bin/$serverName").exists()
    }

    override suspend fun isUpdatable(context: Context): Boolean {
        return false
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/kotlin-language-server"))
    }
}
