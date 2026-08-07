// DO NOT UPDATE PACKAGE NAME OTHERWISE EXTENSIONS WILL BREAK
package com.rk.extension

import com.rk.settings.CachedExtensionPreference
import com.rk.settings.Preference
import kotlin.properties.ReadWriteProperty

interface ExtensionSettings {
    fun getString(key: String, default: String): String?

    fun getBoolean(key: String, default: Boolean): Boolean

    fun getInt(key: String, default: Int): Int

    fun getFloat(key: String, default: Float): Float

    fun getLong(key: String, default: Long): Long

    fun putString(key: String, value: String)

    fun putBoolean(key: String, value: Boolean)

    fun putInt(key: String, value: Int)

    fun putFloat(key: String, value: Float)

    fun putLong(key: String, value: Long)

    fun <T> delegate(key: String, defaultValue: T): ReadWriteProperty<Any?, T>
}

class SharedPrefExtensionSettings(private val id: String) : ExtensionSettings {
    override fun getString(key: String, default: String) = Preference.getString("$id.$key", default)

    override fun getBoolean(key: String, default: Boolean) = Preference.getBoolean("$id.$key", default)

    override fun getInt(key: String, default: Int) = Preference.getInt("$id.$key", default)

    override fun getFloat(key: String, default: Float) = Preference.getFloat("$id.$key", default)

    override fun getLong(key: String, default: Long) = Preference.getLong("$id.$key", default)

    override fun putString(key: String, value: String) = Preference.setString("$id.$key", value)

    override fun putBoolean(key: String, value: Boolean) = Preference.setBoolean("$id.$key", value)

    override fun putInt(key: String, value: Int) = Preference.setInt("$id.$key", value)

    override fun putFloat(key: String, value: Float) = Preference.setFloat("$id.$key", value)

    override fun putLong(key: String, value: Long) = Preference.setLong("$id.$key", value)

    override fun <T> delegate(
        key: String,
        defaultValue: T,
    ): ReadWriteProperty<Any?, T> {
        return CachedExtensionPreference(id, key, defaultValue)
    }
}
