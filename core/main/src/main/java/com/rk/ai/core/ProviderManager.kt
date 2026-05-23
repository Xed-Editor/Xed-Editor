package com.rk.ai.core

import android.util.Log
import com.rk.settings.SecureSettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProviderManager {
    private val providers = LinkedHashMap<String, AiProvider>()
    private val mutex = Mutex()
    private var defaultProviderId: String = ""

    suspend fun register(provider: AiProvider) {
        mutex.withLock {
            providers[provider.id] = provider
            if (defaultProviderId.isBlank()) {
                defaultProviderId = provider.id
            }
            Log.d("ProviderManager", "Registered provider: ${provider.id}")
        }
    }

    suspend fun unregister(providerId: String) {
        mutex.withLock {
            providers.remove(providerId)
            if (defaultProviderId == providerId) {
                defaultProviderId = providers.keys.firstOrNull() ?: ""
            }
        }
    }

    suspend fun getProvider(providerId: String? = null): AiProvider? = mutex.withLock {
        val id = providerId ?: defaultProviderId
        providers[id]
    }

    suspend fun getProviderOrThrow(providerId: String? = null): AiProvider {
        val id = providerId ?: defaultProviderId
        return getProvider(id) ?: throw AiError.ProviderUnavailable(
            id.ifBlank { "default" },
            "No provider registered with id '$id'"
        )
    }

    suspend fun getDefaultProvider(): AiProvider = getProviderOrThrow(null)

    suspend fun getDefaultProviderId(): String = mutex.withLock {
        defaultProviderId.ifBlank { providers.keys.firstOrNull() ?: "" }
    }

    suspend fun setDefaultProvider(providerId: String) {
        mutex.withLock {
            require(providers.containsKey(providerId)) { "Provider not registered: $providerId" }
            defaultProviderId = providerId
        }
    }

    suspend fun listProviders(): List<AiProvider> = mutex.withLock {
        providers.values.toList()
    }

    suspend fun getProviderIds(): List<String> = mutex.withLock {
        providers.keys.toList()
    }

    suspend fun health(): Map<String, ProviderHealth> {
        val snapshot = mutex.withLock { providers.values.toList() }
        val results = LinkedHashMap<String, ProviderHealth>()
        for (p in snapshot) {
            results[p.id] = try {
                p.health()
            } catch (e: Exception) {
                ProviderHealth(healthy = false, message = e.message ?: "unknown")
            }
        }
        return results
    }

    suspend fun resolveApiKey(providerId: String): String {
        val keyPrefKey = "ai_api_key_$providerId"
        return SecureSettingsStore.get(keyPrefKey)
            .ifBlank { SecureSettingsStore.get("ai_api_key") }
    }

    init {
        Log.d("ProviderManager", "Initialized")
    }
}
