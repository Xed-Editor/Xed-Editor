package com.rk.lsp

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.lsp.servers.ExternalProcessServer
import com.rk.lsp.servers.ExternalSocketServer
import com.rk.settings.Preference
import java.io.Serializable

data class SavedLspConfig(
    val type: String, // socket or process
    val supportedExtensions: List<String>,
    val host: String = "localhost", // Specific to socket
    val port: Int = 0, // Specific to socket
    val command: String = "", // Specific to process
) : Serializable

object LspPersistence {
    // TODO: Do not save in settings but in separate file (maybe also without GSON, not only here, but with native java
    // serialization)
    private const val KEY_EXTERNAL_LSP = "saved_external_servers"
    private val gson = Gson()

    fun saveServers() {
        val configList =
            LspRegistry.externalServers.mapNotNull { server ->
                when (server) {
                    is ExternalSocketServer -> {
                        SavedLspConfig(
                            type = "socket",
                            host = server.host,
                            port = server.port,
                            supportedExtensions = server.supportedExtensions,
                        )
                    }
                    is ExternalProcessServer -> {
                        SavedLspConfig(
                            type = "process",
                            command = server.command,
                            supportedExtensions = server.supportedExtensions,
                        )
                    }
                    else -> null
                }
            }

        val json = gson.toJson(configList)
        Preference.setString(KEY_EXTERNAL_LSP, json)
    }

    fun restoreServers() {
        val json = Preference.getString(KEY_EXTERNAL_LSP, "")
        if (json.isEmpty()) return

        val type = object : TypeToken<List<SavedLspConfig>>() {}.type
        val configs: List<SavedLspConfig> = gson.fromJson(json, type)

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
            server?.let { LspRegistry.externalServers.add(it) }
        }
    }
}
