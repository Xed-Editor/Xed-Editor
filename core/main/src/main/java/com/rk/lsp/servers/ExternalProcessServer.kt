package com.rk.lsp.servers

import android.content.Context
import com.rk.file.FileTypeManager
import com.rk.lsp.LspConnectionConfig
import com.rk.lsp.LspServer
import kotlin.random.Random

// DO not put this in lsp registry
data class ExternalProcessServer(
    val command: String,
    override val supportedExtensions: List<String>,
    override val id: String = "${supportedExtensions.firstOrNull()}_${Random.nextInt()}",
) : LspServer() {
    override val languageName = supportedExtensions.firstOrNull()?.let { FileTypeManager.fromExtension(it).title } ?: ""
    override val serverName = command
    override val icon = supportedExtensions.firstOrNull()?.let { FileTypeManager.fromExtension(it).icon }
    override val canBeUninstalled = false

    override suspend fun isInstalled(context: Context) = true

    override fun install(context: Context) {}

    override fun uninstall(context: Context) {}

    override suspend fun isUpdatable(context: Context) = false

    override fun update(context: Context) {}

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(
            arrayOf("bash", "-c", "[ -f ~/.bashrc ] && source ~/.bashrc; [ -f /initrc ] && source /initrc; $command")
        )
    }

    override fun toString() = serverName
}
