package com.rk.lsp

import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.lsp.servers.ExternalProcessServer
import com.rk.lsp.servers.ExternalSocketServer
import com.rk.settings.Preference
import com.rk.utils.application
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SavedLspConfig(
    val type: String,
    val supportedExtensions: List<String>,
    val host: String = "localhost", // specific to socket
    val port: Int = 0, // specific to socket
    val command: String = "", // specific to process
)

object LspPersistence {

    @Deprecated("This is now saved as a file.") private const val KEY_EXTERNAL_LSP = "saved_external_servers"

    @Deprecated("This is temporary migration code.")
    fun migrate() {
        runCatching {
            saveFile.writeText(Preference.getString(KEY_EXTERNAL_LSP, ""))
            Preference.removeKey(KEY_EXTERNAL_LSP)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val saveFile = application!!.filesDir.child("externalLSPServer").createFileIfNot()

    fun saveServers() {
        val configList =
            LspRegistry.externalServers.mapNotNull { server ->
                when (server) {
                    is ExternalSocketServer ->
                        SavedLspConfig(
                            type = "socket",
                            host = server.host,
                            port = server.port,
                            supportedExtensions = server.supportedExtensions,
                        )
                    is ExternalProcessServer ->
                        SavedLspConfig(
                            type = "process",
                            command = server.command,
                            supportedExtensions = server.supportedExtensions,
                        )
                    else -> null
                }
            }

        saveFile.writeText(json.encodeToString(configList))
    }

    fun restoreServers() {
        val jsonStr = saveFile.readText()
        if (jsonStr.isEmpty()) return

        val configs =
            runCatching { json.decodeFromString<List<SavedLspConfig>>(jsonStr) }
                .getOrElse {
                    return
                }

        configs.forEach { config ->
            val server =
                when (config.type) {
                    "socket" ->
                        ExternalSocketServer(
                            host = config.host,
                            port = config.port,
                            supportedExtensions = config.supportedExtensions,
                        )
                    "process" ->
                        ExternalProcessServer(
                            command = config.command,
                            supportedExtensions = config.supportedExtensions,
                        )
                    else -> null
                }
            server?.let { LspRegistry.addExternalServer(it) }
        }
    }
}
