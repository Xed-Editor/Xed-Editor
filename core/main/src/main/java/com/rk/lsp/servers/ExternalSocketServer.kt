package com.rk.lsp.servers

import android.app.Activity
import android.content.Context
import com.rk.file.FileTypeManager
import com.rk.lsp.LspConnectionConfig
import com.rk.lsp.LspServer
import kotlin.random.Random

// DO not put this in lsp registry
data class ExternalSocketServer(
    val host: String,
    val port: Int,
    override val supportedExtensions: List<String>,
    override val id: String = "${supportedExtensions.firstOrNull()}_${Random.nextInt()}",
) : LspServer() {
    override val languageName = supportedExtensions.firstOrNull()?.let { FileTypeManager.fromExtension(it).title } ?: ""

    override val serverName = "$host:$port"
    override val icon = supportedExtensions.firstOrNull()?.let { FileTypeManager.fromExtension(it).icon }
    override val canBeUninstalled = false

    override suspend fun isInstalled(context: Context) = true

    override fun install(activity: Activity) {}

    override fun uninstall(activity: Activity) {}

    override suspend fun hasUpdate(context: Context) = false

    override fun update(activity: Activity) {}

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Socket(host = host, port = port)
    }

    override fun toString() = serverName
}
