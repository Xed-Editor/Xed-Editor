package com.rk.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class CachedPreference<T>(val key: String, val defaultValue: T) : ReadWriteProperty<Any?, T> {
    private var state by mutableStateOf(loadInitialValue())

    init {
        Preference.registerDelegate(key, this)
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadInitialValue(): T {
        return when (defaultValue) {
            is Boolean -> Preference.getBoolean(key, defaultValue) as T
            is String -> Preference.getString(key, defaultValue) as T
            is Int -> Preference.getInt(key, defaultValue) as T
            is Long -> Preference.getLong(key, defaultValue) as T
            is Float -> Preference.getFloat(key, defaultValue) as T
            else -> throw IllegalArgumentException("Unsupported preference type: ${defaultValue::class.java.simpleName}")
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = state

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        when (value) {
            is Boolean -> Preference.setBoolean(key, value)
            is String -> Preference.setString(key, value)
            is Int -> Preference.setInt(key, value)
            is Long -> Preference.setLong(key, value)
            is Float -> Preference.setFloat(key, value)
            else -> throw IllegalArgumentException("Unsupported preference type: ${value::class.java.simpleName}")
        }
    }

    internal fun applyStateValue(value: T) {
        state = value
    }
}
