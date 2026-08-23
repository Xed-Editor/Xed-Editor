package com.rk.lsp

import android.content.Context
import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus

object LspRegistry {
    private val _extensionServers = MutableStateFlow<List<LspServer>>(emptyList())
    val extensionServers: StateFlow<List<LspServer>> = _extensionServers.asStateFlow()

    private val _externalServers = MutableStateFlow<List<LspServer>>(emptyList())
    val externalServers: StateFlow<List<LspServer>> = _externalServers.asStateFlow()

    private val _builtInServers = MutableStateFlow<List<LspServer>>(emptyList())
    val builtInServers: StateFlow<List<LspServer>> = _builtInServers.asStateFlow()

    private val configuration: MutableMap<LspServer, Boolean> = mutableMapOf()

    suspend fun updateConfiguration(context: Context) {
        (_builtInServers.value + _extensionServers.value + _externalServers.value).forEach { configuration[it] = it.isInstalled(context) }
    }

    suspend fun getConfigurationChanges(context: Context): List<LspServer> {
        return (_builtInServers.value + _extensionServers.value + _externalServers.value).filter {
            val isInstalled = it.isInstalled(context)
            (configuration[it] ?: false) != isInstalled
        }
    }

    fun addExternalServer(server: LspServer) {
        _externalServers.update { it + server }
    }

    fun removeExternalServer(server: LspServer) {
        _externalServers.update { it - server }
    }

    fun clearExternalServers() {
        _externalServers.value = emptyList()
    }

    fun replaceExternalServer(replaceIndex: Int, newServer: LspServer) {
        _externalServers.update { list ->
            if (replaceIndex !in list.indices) list else list.toMutableList().also { it[replaceIndex] = newServer }
        }
    }

    fun getForId(id: String): LspServer? {
        return _builtInServers.value.find { it.id == id }
            ?: _externalServers.value.find { it.id == id }
            ?: _extensionServers.value.find { it.id == id }
    }

    @XedExtensionPoint
    fun registerServer(server: LspServer) {
        _extensionServers.update { list ->
            if (list.contains(server)) list else list + server
        }
    }

    @XedExtensionPoint
    fun unregisterServer(server: LspServer) {
        _extensionServers.update { it - server }
    }

    @ApiStatus.Internal
    // TODO: Temp
    fun addBuiltInServers(vararg servers: LspServer) {
        _builtInServers.update { it + servers }
    }

    @ApiStatus.Internal
    // TODO: Temp
    fun removeBuiltInServers(vararg servers: LspServer) {
        _builtInServers.update { list -> list.filterNot { it in servers } }
    }
}
