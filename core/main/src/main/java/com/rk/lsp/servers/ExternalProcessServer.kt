package com.rk.lsp.servers

import android.content.Context
import com.rk.file.FileObject
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig
import java.net.URI
import kotlin.String
import kotlin.random.Random


//DO not put this in lsp registry
class ExternalProcessServer(override val languageName: String, val command: String, override val supportedExtensions: List<String>) : BaseLspServer() {

    override val id: String = "${languageName}_${Random.nextInt()}"
    override val serverName: String = command


    override fun isInstalled(context: Context): Boolean {
        return true
    }

    override fun install(context: Context) {}

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("bash","-c",command))
    }

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
        if (other !is ExternalProcessServer){
            return false
        }
        return other.command == command && supportedExtensions.containsAll(other.supportedExtensions)
    }

    override fun hashCode(): Int {
        var result = languageName.hashCode()
        result = 31 * result + command.hashCode()
        result = 31 * result + supportedExtensions.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + serverName.hashCode()
        return result
    }


}