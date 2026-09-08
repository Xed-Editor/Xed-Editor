package com.rk.lsp

import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.LanguageServerDefinition
import io.github.rosemoe.sora.lsp.editor.LspProject
import java.util.concurrent.ConcurrentHashMap

/**
 * A utility object to temporarily prevent specific LSP servers from being used for a project.
 *
 * This is useful in scenarios where a server needs to be disabled on-demand without permanently removing its
 * configuration. For example, a user might want to temporarily stop a server that is causing issues.
 *
 * When a server is "prevented" via `register()`:
 * 1. It is added to a list of prevented servers for the given project.
 * 2. Its current [LanguageServerDefinition] is cached.
 * 3. The definition is then removed from the `LspProject`, effectively disabling it.
 *
 * `unregister()` reverses this process by restoring the cached definition to the project.
 */
object DefinitionPrevention {
    private val preventedServers = ConcurrentHashMap<LspProject, List<LspServer>>()
    private val cachedDefinitions = ConcurrentHashMap<LspProject, Map<LspServer, LanguageServerDefinition>>()

    fun register(project: LspProject, server: LspServer) {
        preventedServers[project] = preventedServers[project]?.plus(server) ?: listOf(server)
        server.supportedExtensions.firstOrNull()?.let {
            val currentDefinition = project.getServerDefinition(it, server.serverName) ?: return@let
            cachedDefinitions[project] =
                cachedDefinitions[project]?.plus(server to currentDefinition) ?: mapOf(server to currentDefinition)
        }
        server.supportedExtensions.forEach { project.removeServerDefinition(it, server.serverName) }
    }

    fun unregister(project: LspProject, server: LspServer) {
        val remainingServers = preventedServers[project]?.minus(server) ?: emptyList()
        if (remainingServers.isEmpty()) {
            preventedServers.remove(project)
        } else {
            preventedServers[project] = remainingServers
        }

        cachedDefinitions[project]?.get(server)?.let { project.addServerDefinition(it) }
        val remainingDefinitions = cachedDefinitions[project]?.minus(server) ?: emptyMap()
        if (remainingDefinitions.isEmpty()) {
            cachedDefinitions.remove(project)
        } else {
            cachedDefinitions[project] = remainingDefinitions
        }
    }

    fun isServerPrevented(project: LspProject, server: LspServer): Boolean {
        return preventedServers[project]?.contains(server) ?: false
    }
}
