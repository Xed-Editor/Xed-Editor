package com.rk.lsp

import androidx.compose.runtime.mutableStateListOf
import com.rk.lsp.servers.Bash
import com.rk.lsp.servers.CSS
import com.rk.lsp.servers.Emmet
import com.rk.lsp.servers.HTML
import com.rk.lsp.servers.JSON
import com.rk.lsp.servers.Python
import com.rk.lsp.servers.TypeScript

object LspRegistry {
    private val mutableServers = mutableStateListOf<BaseLspServer>()
    val extensionServers: List<BaseLspServer>
        get() = mutableServers.toList()

    val builtInServer = listOf(Python(), HTML(), Emmet(), CSS(), TypeScript(), JSON(), Bash())
    val externalServers = mutableStateListOf<BaseLspServer>()

    fun getForId(id: String): BaseLspServer? {
        return builtInServer.find { it.id == id }
            ?: externalServers.find { it.id == id }
            ?: mutableServers.find { it.id == id }
    }

    fun registerServer(server: BaseLspServer) {
        if (!mutableServers.contains(server)) {
            mutableServers.add(server)
        }
    }

    fun unregisterServer(server: BaseLspServer) {
        mutableServers.remove(server)
    }
}
