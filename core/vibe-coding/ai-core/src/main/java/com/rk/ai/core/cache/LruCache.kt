package com.rk.ai.core.cache

import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LruCache<K, V>(
    private val capacity: Int,
    private val store: CacheStore<K, V>,
    private val deleteOnEvict: Boolean = false,
    preloadFromStore: Boolean = false,
    private val expireAfterWriteMillis: Long? = null
) where K : Any {
    private val lock = ReentrantLock()

    private val map = object : LinkedHashMap<K, CacheEntry<V>>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, CacheEntry<V>>): Boolean {
            val shouldEvict = size > capacity
            if (shouldEvict) {
                if (deleteOnEvict) {
                    try {
                        store.remove(eldest.key)
                    } catch (_: Exception) {
                    }
                }
            }
            return shouldEvict
        }
    }

    init {
        if (preloadFromStore) {
            try {
                val all = store.loadAllEntries()
                lock.withLock {
                    val now = now()
                    for ((k, entry) in all) {
                        if (!entry.isExpired(now)) {
                            map[k] = entry
                        } else {
                            runCatching { store.remove(k) }
                        }
                        if (map.size >= capacity) break
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun get(key: K): V? {
        lock.withLock {
            map[key]?.let { entry ->
                if (!entry.isExpired(now())) return entry.value
                map.remove(key)
            }
        }
        val entry = store.loadEntry(key)
        if (entry != null) {
            return if (!entry.isExpired(now())) {
                lock.withLock { map[key] = entry }
                entry.value
            } else {
                runCatching { store.remove(key) }
                null
            }
        }
        return null
    }

    fun put(key: K, value: V) = put(key, value, expireAfterWriteMillis)

    fun put(key: K, value: V, ttlMillis: Long?) {
        val entry = CacheEntry(value = value, expiresAt = ttlMillis?.let { now() + it })
        lock.withLock { map[key] = entry }
        try {
            store.saveEntry(key, entry)
        } catch (_: Exception) {
        }
    }

    fun remove(key: K) {
        lock.withLock { map.remove(key) }
        try {
            store.remove(key)
        } catch (_: Exception) {
        }
    }

    fun clear() {
        lock.withLock { map.clear() }
        try {
            store.clear()
        } catch (_: Exception) {
        }
    }

    fun containsKey(key: K): Boolean {
        val inMem = lock.withLock { map[key]?.let { !it.isExpired(now()) } ?: false }
        if (inMem) return true
        val entry = store.loadEntry(key)
        if (entry != null && !entry.isExpired(now())) return true
        if (entry != null) runCatching { store.remove(key) }
        return false
    }

    fun size(): Int = lock.withLock { map.size }

    fun keysInMemory(): Set<K> = lock.withLock { map.filterValues { !it.isExpired(now()) }.keys.toSet() }
}

private fun now(): Long = System.currentTimeMillis()

