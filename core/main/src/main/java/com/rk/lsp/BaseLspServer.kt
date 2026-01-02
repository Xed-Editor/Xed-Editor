package com.rk.lsp

import android.content.Context
import com.rk.file.FileObject
import java.net.URI

abstract class BaseLspServer {
    abstract fun isInstalled(context: Context): Boolean

    abstract fun install(context: Context)

    abstract fun getConnectionConfig(): LspConnectionConfig

    open suspend fun beforeConnect() {}

    open suspend fun connectionSuccess(lspConnector: BaseLspConnector) {}

    open suspend fun connectionFailure(msg: String?) {}

    open fun getInitializationOptions(uri: URI?): Any? = null

    abstract fun isSupported(file: FileObject): Boolean

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BaseLspServer
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    abstract val id: String
    abstract val languageName: String
    abstract val serverName: String
    abstract val supportedExtensions: List<String>
}
