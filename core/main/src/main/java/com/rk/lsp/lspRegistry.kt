package com.rk.lsp

import androidx.compose.runtime.mutableStateListOf
import com.rk.lsp.servers.Bash
import com.rk.lsp.servers.CSS
import com.rk.lsp.servers.Emmet
import com.rk.lsp.servers.HTML
import com.rk.lsp.servers.JSON
import com.rk.lsp.servers.Python
import com.rk.lsp.servers.TypeScript

val builtInServer = listOf(Python(), HTML(), CSS(), TypeScript(), Emmet(), JSON(), Bash())
val externalServers = mutableStateListOf<BaseLspServer>()

object ExtensionLspRegistry {
    private val mutableServers = mutableStateListOf<BaseLspServer>()
    val servers: List<BaseLspServer>
        get() = mutableServers.toList()

    fun registerServer(server: BaseLspServer) {
        if (!servers.contains(server)) {
            mutableServers.add(server)
        }
    }

    fun unregisterServer(server: BaseLspServer) {
        mutableServers.remove(server)
    }
}
