package com.rk.ai.provider

import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Registering an existing id replaces it; unregistering an override restores the built-in. */
object AiProviderRegistry {
    private val lock = Any()
    private val providersById = LinkedHashMap<String, AiProvider>()
    private var builtinsById: Map<String, AiProvider> = emptyMap()
    private val _providers = MutableStateFlow<List<AiProvider>>(emptyList())
    private var builtinsInstalled = false

    val providers: StateFlow<List<AiProvider>> = _providers.asStateFlow()

    fun find(id: String): AiProvider? {
        installBuiltins()
        return _providers.value.firstOrNull { it.id == id }
    }

    /** Call from `onLoad`; unregister from `onDispose`. */
    @XedExtensionPoint
    fun registerProvider(provider: AiProvider) {
        synchronized(lock) {
            providersById[provider.id] = provider
            publishLocked()
        }
    }

    @XedExtensionPoint
    fun registerProviders(vararg providers: AiProvider) = providers.forEach(::registerProvider)

    @XedExtensionPoint
    fun unregisterProvider(provider: AiProvider) {
        synchronized(lock) { removeOrRestoreLocked(provider.id, provider) }
    }

    @XedExtensionPoint
    fun unregisterProvider(id: String) {
        synchronized(lock) { removeOrRestoreLocked(id, expected = null) }
    }

    /** [configuredId] wins unless its base URL belongs to another provider, to migrate older settings. */
    fun resolveActive(configuredId: String, baseUrl: String): AiProvider? {
        installBuiltins()
        val byId = providersById[configuredId]
        val byUrl = providersById.values.firstOrNull { it.matchesBaseUrl(baseUrl) }
        return when {
            byId != null && (byUrl == null || byId === byUrl) -> byId
            byUrl != null -> byUrl
            else -> byId ?: providersById.values.firstOrNull()
        }
    }

    internal fun installBuiltins() {
        val toInstall =
            synchronized(lock) {
                if (builtinsInstalled) return
                builtinsInstalled = true
                BuiltinProviders.all().associateBy { it.id }
            }
        synchronized(lock) {
            builtinsById = toInstall
            toInstall.forEach { (id, provider) ->
                if (!providersById.containsKey(id)) providersById[id] = provider
            }
            publishLocked()
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            providersById.clear()
            builtinsById = emptyMap()
            _providers.value = emptyList()
            builtinsInstalled = false
        }
    }

    private fun removeOrRestoreLocked(id: String, expected: AiProvider?) {
        val current = providersById[id] ?: return
        if (expected != null && current !== expected) return
        val builtin = builtinsById[id]
        if (builtin != null) {
            providersById[id] = builtin
        } else {
            providersById.remove(id)
        }
        publishLocked()
    }

    private fun publishLocked() {
        _providers.value = providersById.values.toList()
    }
}
