package com.rk.extension

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import com.rk.settings.Preference

class ExtensionContext(val extension: LocalExtension, val hostContext: Context) {
    val settings = SharedPrefExtensionSettings(extension.id)

    val assets: AssetManager by lazy {
        AssetManager::class.java.getDeclaredConstructor().newInstance().apply {
            val method = javaClass.getMethod("addAssetPath", String::class.java)
            method.invoke(this, extension.apkFile.absolutePath)
        }
    }

    val resources by lazy {
        Resources(assets, hostContext.resources.displayMetrics, hostContext.resources.configuration)
    }
}

interface ExtensionSettings {
    fun getString(key: String, default: String): String?

    fun getBoolean(key: String, default: Boolean): Boolean

    fun getInt(key: String, default: Int): Int

    fun putString(key: String, value: String)

    fun putBoolean(key: String, value: Boolean)

    fun putInt(key: String, value: Int)
}

class SharedPrefExtensionSettings(private val id: String) : ExtensionSettings {
    override fun getString(key: String, default: String) = Preference.getString("$id.$key", default)

    override fun getBoolean(key: String, default: Boolean) = Preference.getBoolean("$id.$key", default)

    override fun getInt(key: String, default: Int) = Preference.getInt("$id.$key", default)

    override fun putString(key: String, value: String) = Preference.setString("$id.$key", value)

    override fun putBoolean(key: String, value: Boolean) = Preference.setBoolean("$id.$key", value)

    override fun putInt(key: String, value: Int) = Preference.setInt("$id.$key", value)
}
