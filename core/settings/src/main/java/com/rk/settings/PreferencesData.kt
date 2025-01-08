package com.rk.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build

@Suppress("NOTHING_TO_INLINE")
object PreferencesData {
    const val PREFNAME = "Settings"

    inline fun isDarkMode(ctx: Context): Boolean {
        return ((ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES)
    }

    inline fun isOled(): Boolean {
        return getBoolean(PreferencesKeys.OLED, false)
    }

    inline fun isMonet(): Boolean {
        return getBoolean(PreferencesKeys.MONET, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    }


    inline fun getBoolean(key: String?, default: Boolean): Boolean {
        return sharedPreferences!!.getBoolean(key, default)
    }

    inline fun removeKey(key: String?) {
        sharedPreferences!!.edit().remove(key).apply()
    }

    inline fun setBoolean(key: String?, value: Boolean) {
        val editor = sharedPreferences!!.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    inline fun initPref(ctx: Context) {
        sharedPreferences =
            ctx.applicationContext.getSharedPreferences(PREFNAME, Context.MODE_PRIVATE)
    }

    var sharedPreferences: SharedPreferences? = null


    inline fun getString(key: String?, default: String): String {
        return sharedPreferences!!.getString(key, default) ?: default
    }


    inline fun setString(key: String?, value: String?) {
        val editor = sharedPreferences!!.edit()
        editor.putString(key, value)
        editor.apply()
    }
}
