package com.rk.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build

object Settings {
    private const val PREF_NAME = "Settings"

    private val stringCache = hashMapOf<String, String?>()
    private val boolCache = hashMapOf<String, Boolean>()

    fun isDarkMode(ctx: Context): Boolean {
        return ((ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    }

    fun isOled(): Boolean {
        return getBoolean(SettingsKey.OLED, false)
    }

    fun isMonet(): Boolean {
        return getBoolean(SettingsKey.MONET, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    }


    fun getBoolean(key: String, default: Boolean): Boolean {
        runCatching {
            return boolCache[key] ?: sharedPreferences!!.getBoolean(key, default)
                .also { boolCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setBoolean(key, default)
        }
        return default
    }

    fun removeKey(key: String) {
        boolCache.remove(key)
        stringCache.remove(key)
        sharedPreferences!!.edit().remove(key).apply()
    }

    fun setBoolean(key: String, value: Boolean) {
        boolCache[key] = value
        runCatching {
            val editor = sharedPreferences!!.edit()
            editor.putBoolean(key, value)
            editor.apply()
        }.onFailure { it.printStackTrace() }
    }

    fun initPref(ctx: Context) {
        sharedPreferences = ctx.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private var sharedPreferences: SharedPreferences? = null

    fun getString(key: String, default: String): String {
        runCatching {
            return stringCache[key] ?: sharedPreferences!!.getString(key, default)!!
                .also { stringCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setString(key, default)
        }
        return default
    }

    fun setString(key: String, value: String?) {
        stringCache[key] = value
        runCatching {
            val editor = sharedPreferences!!.edit()
            editor.putString(key, value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }

    }

}
