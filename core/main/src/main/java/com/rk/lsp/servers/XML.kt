package com.rk.lsp.servers

import android.content.Context
import com.rk.exec.isTerminalInstalled
import com.rk.file.BuiltinFileType
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxHomeDir
import com.rk.lsp.LspConnectionConfig
import com.rk.lsp.ScriptedLspServer

object XML : ScriptedLspServer() {
    override val id: String = "xml"
    override val languageName: String = "XML"
    override val serverName = "lemminx"
    override val supportedExtensions = BuiltinFileType.XML.extensions
    override val icon = BuiltinFileType.XML.icon

    override val installScript = localBinDir().child("lsp/xml")
    override val installId = "XML language server"

    // Has to be manually updated when a new version is released (Don't forgot to also update xml.sh)
    const val LATEST_VERSION = "0.31.0"

    override suspend fun isInstalled(context: Context): Boolean {
        if (!isTerminalInstalled()) {
            return false
        }

        return sandboxHomeDir().child(".lsp/lemminx/server.jar").exists()
    }

    override suspend fun isUpdatable(context: Context): Boolean {
        val versionFile = sandboxHomeDir().child(".lsp/lemminx/version.txt")
        val currentVersion = runCatching { versionFile.readText().trim() }.getOrNull()
        return currentVersion != LATEST_VERSION
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("java", "-jar", "/home/.lsp/lemminx/server.jar"))
    }
}
