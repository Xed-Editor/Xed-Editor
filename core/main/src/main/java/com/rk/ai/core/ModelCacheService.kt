package com.rk.ai.core

import android.util.LruCache

data class CachedModelList(
    val models: List<ModelInfo>,
    val fetchedAt: Long,
    val providerId: String,
) {
    fun isStale(ttlMs: Long = 300_000L): Boolean =
        System.currentTimeMillis() - fetchedAt > ttlMs
}

class ModelCacheService(
    private val maxEntries: Int = 32,
    private val defaultTtlMs: Long = 300_000L,
) {
    private val cache = object : LruCache<String, CachedModelList>(maxEntries) {}

    fun get(providerId: String, ttlMs: Long = defaultTtlMs): List<ModelInfo>? {
        val entry = cache.get(providerId) ?: return null
        if (entry.isStale(ttlMs)) {
            cache.remove(providerId)
            return null
        }
        return entry.models
    }

    fun put(providerId: String, models: List<ModelInfo>) {
        cache.put(providerId, CachedModelList(
            models = models,
            fetchedAt = System.currentTimeMillis(),
            providerId = providerId,
        ))
    }

    fun invalidate(providerId: String) {
        cache.remove(providerId)
    }

    fun invalidateAll() {
        cache.evictAll()
    }

    fun getCachedProviderIds(): List<String> {
        val ids = mutableListOf<String>()
        val snapshot = cache.snapshot()
        for (entry in snapshot) {
            if (!entry.value.isStale(defaultTtlMs)) {
                ids.add(entry.key)
            }
        }
        return ids
    }
}
