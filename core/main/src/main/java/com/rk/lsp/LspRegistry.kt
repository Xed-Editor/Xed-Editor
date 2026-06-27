package com.rk.lsp

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.rk.extension.api.XedExtensionPoint

object LspRegistry {
    private val _extensionServers = mutableStateListOf<LspServer>()
    val extensionServers: List<LspServer>
        get() = _extensionServers.toList()

    private val _externalServers = mutableStateListOf<LspServer>()
    val externalServers: List<LspServer>
        get() = _externalServers.toList()

    private val configuration: MutableMap<LspServer, Boolean> = mutableMapOf()

    suspend fun updateConfiguration(context: Context) {
      externalServers.forEach { configuration[it] = it.isInstalled(context) }
    }

    suspend fun getConfigurationChanges(context: Context): List<LspServer> {
        return externalServers.filter {
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
        return _externalServers.find { it.id == id }
            ?: _extensionServers.find { it.id == id }
    }

    @XedExtensionPoint
    fun registerServer(server: LspServer) {
        if (!_extensionServers.contains(server)) {
            _extensionServers.add(server)
        }
    }

    @XedExtensionPoint
    fun unregisterServer(server: LspServer) {
        _extensionServers.remove(server)
    }
}
