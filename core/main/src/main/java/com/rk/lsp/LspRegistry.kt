package com.rk.lsp

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.rk.extension.api.XedExtensionPoint
import org.jetbrains.annotations.ApiStatus

object LspRegistry {
    private val _extensionServers = mutableStateListOf<LspServer>()
    val extensionServers: List<LspServer>
        get() = _extensionServers.toList()

    private val _externalServers = mutableStateListOf<LspServer>()
    val externalServers: List<LspServer>
        get() = _externalServers.toList()

    private val _builtInServers = mutableStateListOf<LspServer>()
    val builtInServers: List<LspServer>
        get() = _builtInServers.toList()

    private val configuration: MutableMap<LspServer, Boolean> = mutableMapOf()

    suspend fun updateConfiguration(context: Context) {
        (builtInServers + extensionServers + externalServers).forEach { configuration[it] = it.isInstalled(context) }
    }

    suspend fun getConfigurationChanges(context: Context): List<LspServer> {
        return (builtInServers + extensionServers + externalServers).filter {
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
        return _builtInServers.find { it.id == id }
            ?: _externalServers.find { it.id == id }
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

    @ApiStatus.Internal
    // TODO: Temp
    fun addBuiltInServers(vararg servers: LspServer) {
        _builtInServers.addAll(servers)
    }
}
