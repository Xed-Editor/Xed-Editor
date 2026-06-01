package com.rk.ai.core.cache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PerKeyFileCacheStore<K : Any, V : Any>(
    private val dir: File,
    private val keyCodec: KeyCodec<K>,
    private val valueSerializer: KSerializer<V>,
    private val json: Json = Json { prettyPrint = false; ignoreUnknownKeys = true }
) : CacheStore<K, V> {
    private val lock = ReentrantLock()
    private val ext = ".json"
    private val entrySerializer = cacheEntrySerializer(valueSerializer)

    override fun loadEntry(key: K): CacheEntry<V>? = lock.withLock {
        val f = fileFor(key)
        if (!f.exists()) return@withLock null
        return@withLock runCatching { json.decodeFromString(entrySerializer, f.readText()) }
            .getOrElse {
                runCatching { json.decodeFromString(valueSerializer, f.readText()) }
                    .getOrNull()?.let { CacheEntry(value = it, expiresAt = null) }
            }
    }

    override fun saveEntry(key: K, entry: CacheEntry<V>) = lock.withLock {
        val f = fileFor(key)
        ensureParentDir(f)
        val text = json.encodeToString(entrySerializer, entry)
        atomicWrite(f, text)
    }

    override fun remove(key: K) = lock.withLock {
        val f = fileFor(key)
        if (f.exists()) {
            runCatching { f.delete() }
        }
    }

    override fun clear() = lock.withLock {
        if (!dir.exists()) return@withLock
        dir.listFiles { file -> file.isFile && file.name.endsWith(ext) }?.forEach { runCatching { it.delete() } }
    }

    override fun loadAllEntries(): Map<K, CacheEntry<V>> = lock.withLock {
        if (!dir.exists()) return@withLock emptyMap()
        val result = LinkedHashMap<K, CacheEntry<V>>()
        dir.listFiles { file -> file.isFile && file.name.endsWith(ext) }?.forEach { file ->
            val base = file.name.removeSuffix(ext)
            val key = keyCodec.fromFileName(base) ?: return@forEach
            val entry = runCatching { json.decodeFromString(entrySerializer, file.readText()) }
                .getOrElse {
                    runCatching { json.decodeFromString(valueSerializer, file.readText()) }
                        .getOrNull()?.let { CacheEntry(value = it, expiresAt = null) }
                }
            if (entry != null) result[key] = entry
        }
        result
    }

    override fun keys(): Set<K> = lock.withLock {
        if (!dir.exists()) return@withLock emptySet()
        buildSet {
            dir.listFiles { file -> file.isFile && file.name.endsWith(ext) }?.forEach { file ->
                val base = file.name.removeSuffix(ext)
                keyCodec.fromFileName(base)?.let { add(it) }
            }
        }
    }

    private fun fileFor(key: K): File {
        val name = keyCodec.toFileName(key) + ext
        return File(dir, name)
    }
}

