package com.rk.lsp.servers

import android.content.Context
import com.rk.file.FileTypeManager
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig
import kotlin.random.Random

// DO not put this in lsp registry
class ExternalSocketServer(val host: String, val port: Int, override val supportedExtensions: List<String>) :
    BaseLspServer() {
    override val languageName = supportedExtensions.firstOrNull()?.let { FileTypeManager.fromExtension(it).title } ?: ""
    override val id: String = "${supportedExtensions.firstOrNull()}_${Random.nextInt()}"
    override val serverName: String = "$host:$port"
    override val icon = supportedExtensions.firstOrNull()?.let { FileTypeManager.fromExtension(it).icon }

    override fun isInstalled(context: Context): Boolean {
        return true
    }

    override fun install(context: Context) {}

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Socket(host = host, port = port)
    }

    override fun toString(): String {
        return serverName
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ExternalSocketServer

        if (port != other.port) return false
        if (icon != other.icon) return false
        if (host != other.host) return false
        if (supportedExtensions != other.supportedExtensions) return false
        if (languageName != other.languageName) return false
        if (id != other.id) return false
        if (serverName != other.serverName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + port
        result = 31 * result + (icon ?: 0)
        result = 31 * result + host.hashCode()
        result = 31 * result + supportedExtensions.hashCode()
        result = 31 * result + languageName.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + serverName.hashCode()
        return result
    }
}
