package com.rk.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.rk.utils.application
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

object Preference {
    private val sharedPreferences: SharedPreferences by lazy {
        val ctx = application ?: error("Application context not initialized")
        ctx.getSharedPreferences("Settings", Context.MODE_PRIVATE)
    }

    val preferenceTypes: Map<String, KClass<*>> by lazy {
        Settings::class
            .declaredMemberProperties
            .mapNotNull { prop ->
                try {
                    prop.isAccessible = true
                    val delegate = prop.getDelegate(Settings)
                    if (delegate is CachedPreference<*>) {
                        val default = delegate.defaultValue
                        if (default != null) delegate.key to default::class else null
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
            .toMap()
    }

    private val delegateRegistry = ConcurrentHashMap<String, CachedPreference<*>>()

    internal fun registerDelegate(key: String, delegate: CachedPreference<*>) {
        delegateRegistry[key] = delegate
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> notifyDelegate(key: String, value: T) {
        (delegateRegistry[key] as? CachedPreference<T>)?.applyStateValue(value)
    }

    private val cache = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> getCached(key: String): T? = (cache[key] as? T)

    private fun <T> setCached(key: String, value: T) {
        if (value != null) {
            cache[key] = value as Any
        } else {
            cache.remove(key)
        }
    }

    private fun clearKeyFromCache(key: String) {
        cache.remove(key)
    }

    fun getAll(): Map<String, Any?> = sharedPreferences.all

    fun put(key: String, value: Any) {
        when (value) {
            is String -> setString(key, value)
            is Boolean -> setBoolean(key, value)
            is Int -> setInt(key, value)
            is Long -> setLong(key, value)
            is Float -> setFloat(key, value)
            else -> throw IllegalArgumentException("Unsupported preference type: ${value::class.java.simpleName}")
        }
    }

    suspend fun preloadAllSettings() {
        Settings::class.members.forEach { member ->
            if (member is KProperty<*>) {
                runCatching { member.getter.call(Settings) }
            }
        }
    }

    fun clearData() {
        sharedPreferences.edit(commit = true) { clear() }
        cache.clear()
    }

    fun clearCaches() {
        cache.clear()
    }

    fun removeKey(key: String) {
        if (!sharedPreferences.contains(key)) return
        sharedPreferences.edit { remove(key) }
        clearKeyFromCache(key)
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        return getCached(key) ?: run {
            val value = runCatching { sharedPreferences.getBoolean(key, default) }
                .getOrElse {
                    setBoolean(key, default)
                    default
                }
            setCached(key, value)
            value
        }
    }

    fun setBoolean(key: String, value: Boolean) {
        notifyDelegate(key, value)
        setCached(key, value)
        runCatching { sharedPreferences.edit { putBoolean(key, value) } }
    }

    fun getString(key: String, default: String): String {
        return getCached(key) ?: run {
            val value = runCatching { sharedPreferences.getString(key, default) ?: default }
                .getOrElse {
                    setString(key, default)
                    default
                }
            setCached(key, value)
            value
        }
    }

    fun setString(key: String, value: String?) {
        notifyDelegate(key, value)
        setCached(key, value)
        runCatching { sharedPreferences.edit { putString(key, value) } }
    }

    fun getInt(key: String, default: Int): Int {
        return getCached(key) ?: run {
            val value = runCatching { sharedPreferences.getInt(key, default) }
                .getOrElse {
                    setInt(key, default)
                    default
                }
            setCached(key, value)
            value
        }
    }

    fun setInt(key: String, value: Int) {
        notifyDelegate(key, value)
        setCached(key, value)
        runCatching { sharedPreferences.edit { putInt(key, value) } }
    }

    fun getLong(key: String, default: Long): Long {
        return getCached(key) ?: run {
            val value = runCatching { sharedPreferences.getLong(key, default) }
                .getOrElse {
                    setLong(key, default)
                    default
                }
            setCached(key, value)
            value
        }
    }

    fun setLong(key: String, value: Long) {
        notifyDelegate(key, value)
        setCached(key, value)
        runCatching { sharedPreferences.edit { putLong(key, value) } }
    }

    fun getFloat(key: String, default: Float): Float {
        return getCached(key) ?: run {
            val value = runCatching { sharedPreferences.getFloat(key, default) }
                .getOrElse {
                    setFloat(key, default)
                    default
                }
            setCached(key, value)
            value
        }
    }

    fun setFloat(key: String, value: Float) {
        notifyDelegate(key, value)
        setCached(key, value)
        runCatching { sharedPreferences.edit { putFloat(key, value) } }
    }
}
