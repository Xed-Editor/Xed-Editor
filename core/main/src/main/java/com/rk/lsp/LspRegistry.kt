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
    private val mutableServers = mutableStateListOf<LspServer>()
    val extensionServers: List<LspServer>
        get() = mutableServers.toList()

    val builtInServer = listOf(Python, HTML, Emmet, CSS, TypeScript, JSON, Bash, XML)
    val externalServers = mutableStateListOf<LspServer>()

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

    fun getForId(id: String): LspServer? {
        return builtInServer.find { it.id == id }
            ?: externalServers.find { it.id == id }
            ?: mutableServers.find { it.id == id }
    }

    fun registerServer(server: LspServer) {
        if (!mutableServers.contains(server)) {
            mutableServers.add(server)
        }
    }

    fun unregisterServer(server: LspServer) {
        mutableServers.remove(server)
    }
}
