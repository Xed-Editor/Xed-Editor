package com.rk.lsp

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.rk.lsp.servers.Bash
import com.rk.lsp.servers.CSS
import com.rk.lsp.servers.Emmet
import com.rk.lsp.servers.HTML
import com.rk.lsp.servers.JSON
import com.rk.lsp.servers.Python
import com.rk.lsp.servers.TypeScript
import com.rk.lsp.servers.XML

object LspRegistry {
    private val _extensionServers = mutableStateListOf<LspServer>()
    val extensionServers: List<LspServer>
        get() = _extensionServers.toList()

    val builtInServer = listOf(Python, HTML, Emmet, CSS, TypeScript, JSON, Bash, XML)

    private val _externalServers = mutableStateListOf<LspServer>()
    val externalServers: List<LspServer>
        get() = _externalServers.toList()

    private val configuration: MutableMap<LspServer, Boolean> = mutableMapOf()

    suspend fun updateConfiguration(context: Context) {
        (builtInServer + extensionServers).forEach { configuration[it] = it.isInstalled(context) }
    }

    suspend fun getConfigurationChanges(context: Context): List<LspServer> {
        return (builtInServer + extensionServers).filter {
            val isInstalled = it.isInstalled(context)
            (configuration[it] ?: false) != isInstalled
        }
    }

    fun addExternalServer(server: LspServer) {
        _externalServers.add(server)
    }

    fun removeExternalServer(server: LspServer) {
        _externalServers.remove(server)
    }

    fun clearExternalServers() {
        _externalServers.clear()
    }

    fun replaceExternalServer(replaceIndex: Int, newServer: LspServer) {
        _externalServers[replaceIndex] = newServer
    }

    fun getForId(id: String): LspServer? {
        return builtInServer.find { it.id == id }
            ?: _externalServers.find { it.id == id }
            ?: _extensionServers.find { it.id == id }
    }

    fun registerServer(server: LspServer) {
        if (!_extensionServers.contains(server)) {
            _extensionServers.add(server)
        }
    }

    fun unregisterServer(server: LspServer) {
        _extensionServers.remove(server)
    }
}
