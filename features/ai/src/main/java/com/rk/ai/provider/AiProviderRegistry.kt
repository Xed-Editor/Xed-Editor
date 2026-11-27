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
    private var customById: Map<String, AiProvider> = emptyMap()
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

    @XedExtensionPoint fun registerProviders(vararg providers: AiProvider) = providers.forEach(::registerProvider)

    @XedExtensionPoint
    fun unregisterProvider(provider: AiProvider) {
        synchronized(lock) { removeOrRestoreLocked(provider.id, provider) }
    }

    @XedExtensionPoint
    fun unregisterProvider(id: String) {
        synchronized(lock) { removeOrRestoreLocked(id, expected = null) }
    }

    /** The configured provider, or the first registered one when the id is unknown. */
    fun resolveActive(configuredId: String): AiProvider {
        installBuiltins()
        return providersById[configuredId] ?: providersById.values.firstOrNull() ?: BuiltinProviders.default()
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
            toInstall.forEach { (id, provider) -> if (!providersById.containsKey(id)) providersById[id] = provider }
            publishLocked()
        }
    }

    /**
     * Replaces the user-configured providers. Provider ids an extension registered itself are left alone, and a
     * built-in id is never taken over by a custom provider.
     */
    internal fun installCustom(providers: List<AiProvider>) {
        installBuiltins()
        synchronized(lock) {
            val next = providers.associateBy { it.id }
            (customById.keys - next.keys).forEach { providersById.remove(it) }
            next.forEach { (id, provider) -> if (!builtinsById.containsKey(id)) providersById[id] = provider }
            customById = next
            publishLocked()
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            providersById.clear()
            builtinsById = emptyMap()
            customById = emptyMap()
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
