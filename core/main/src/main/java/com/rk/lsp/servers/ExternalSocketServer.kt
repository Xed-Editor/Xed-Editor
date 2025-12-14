package com.rk.lsp.servers

import android.content.Context
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.lsp.BaseLspConnector
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig
import java.net.URI
import kotlin.random.Random

// DO not put this in lsp registry
class ExternalSocketServer(val host: String, val port: Int, override val supportedExtensions: List<String>) :
    BaseLspServer() {
    override val languageName = supportedExtensions.firstOrNull()?.let { FileType.fromExtension(it).title } ?: ""
    override val id: String = "${languageName}_${Random.nextInt()}"
    override val serverName: String = "$host:$port"
    override val icon = supportedExtensions.firstOrNull()?.let { FileType.fromExtension(it).icon }

    override fun isInstalled(context: Context): Boolean {
        return true
    }

    override fun install(context: Context) {}

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Socket(host = host, port = port)
    }

    override suspend fun beforeConnect() {}

    override suspend fun connectionSuccess(lspConnector: BaseLspConnector) {}

    override suspend fun connectionFailure(msg: String?) {}

    override fun isSupported(file: FileObject): Boolean {
        val fileExt = file.getName().substringAfterLast(".")
        return supportedExtensions.contains(fileExt)
    }

    override fun getInitializationOptions(uri: URI?): Any? {
        return null
    }

    override fun toString(): String {
        return serverName
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ExternalSocketServer) {
            return false
        }
        return other.port == port && other.host == host && supportedExtensions.containsAll(other.supportedExtensions)
    }

    override fun hashCode(): Int {
        var result = port
        result = 31 * result + languageName.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + supportedExtensions.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + serverName.hashCode()
        return result
    }
}
