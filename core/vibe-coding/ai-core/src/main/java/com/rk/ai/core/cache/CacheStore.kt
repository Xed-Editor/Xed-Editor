package com.rk.ai.core.cache

interface CacheStore<K, V> {
    fun loadEntry(key: K): CacheEntry<V>?
    fun saveEntry(key: K, entry: CacheEntry<V>)
    fun remove(key: K)
    fun clear()
    fun loadAllEntries(): Map<K, CacheEntry<V>>
    fun keys(): Set<K>
}

