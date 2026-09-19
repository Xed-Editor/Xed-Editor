package com.rk.ai.provider

import com.rk.extension.api.XedExtensionPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The catalogue of chat backends.
 *
 * Built-ins are installed first (see [BuiltinProviders]); extensions add their own with
 * [registerProvider]. Registering an existing [AiProvider.id] replaces the previous provider, so an
 * extension can override a built-in; unregistering an override restores the built-in it shadowed.
 */
object AiProviderRegistry {
    private val lock = Any()
    private val providersById = LinkedHashMap<String, AiProvider>()
    private var builtinsById: Map<String, AiProvider> = emptyMap()
    private val _providers = MutableStateFlow<List<AiProvider>>(emptyList())
    private var builtinsInstalled = false

    val providers: StateFlow<List<AiProvider>> = _providers.asStateFlow()

    /** The provider with this id, or null when nothing registered it. */
    fun find(id: String): AiProvider? {
        installBuiltins()
        return _providers.value.firstOrNull { it.id == id }
    }

    /** Registers (or replaces) a provider. Call it from `onLoad`; unregister from `onDispose`. */
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

    /**
     * Picks the provider a run should use.
     *
     * [configuredId] wins, except when its base URL clearly belongs to another registered provider -
     * that is how settings written before providers existed are migrated without the user noticing.
     */
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
