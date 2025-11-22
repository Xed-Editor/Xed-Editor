package com.rk.lsp

import android.content.Context
import com.rk.file.FileObject
import java.net.URI

abstract class BaseLspServer {
    abstract fun isInstalled(context: Context): Boolean

    abstract fun install(context: Context)

    abstract fun getConnectionConfig(): LspConnectionConfig

    abstract fun getInitializationOptions(uri: URI?): Any?

    abstract fun isSupported(file: FileObject): Boolean

    abstract val id: String
    abstract val languageName: String
    abstract val serverName: String
    abstract val supportedExtensions: List<String>
}
