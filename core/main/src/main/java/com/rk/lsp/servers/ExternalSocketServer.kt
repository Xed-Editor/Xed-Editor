package com.rk.lsp.servers

import android.content.Context
import com.rk.file.FileObject
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig
import com.rk.tabs.lsp_connections
import java.net.URI
import kotlin.String


//DO not put this in lsp registry
class ExternalSocketServer(val host: String, val port: Int) : BaseLspServer() {

    override val id: String = this::class.java.simpleName
    override val languageName: String = this::class.java.simpleName
    override val serverName: String = this::class.java.simpleName
    override val supportedExtensions: List<String>
        get() {
            val extensions = mutableListOf<String>()
            lsp_connections.keys.forEach { extensions.addAll(it) }
            return extensions
        }

    override fun isInstalled(context: Context): Boolean {
        return true
    }

    override fun install(context: Context) {}

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Socket(host = host, port = port)
    }

    override fun isSupported(file: FileObject): Boolean {
        return true
    }
    override fun getInitializationOptions(uri: URI?): Any? {
        return null
    }
}