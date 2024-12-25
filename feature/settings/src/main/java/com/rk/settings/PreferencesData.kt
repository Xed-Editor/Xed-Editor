package com.rk.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate


@SuppressLint("ApplySharedPref")
object PreferencesData {
    private const val PREFNAME = "Settings"

    fun isDarkMode(ctx: Context): Boolean {
        return ((ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES)
    }

    fun isOled(): Boolean {
        return getBoolean(PreferencesKeys.OLED, false)
    }

    fun isMonet(): Boolean {
        return getBoolean(PreferencesKeys.MONET, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    }
    
    
    fun getBoolean(key: String?, default: Boolean): Boolean {
        return sharedPreferences!!.getBoolean(key, default)
    }
    
    
    fun setBoolean(key: String?, value: Boolean) {
        val editor = sharedPreferences!!.edit()
        editor.putBoolean(key, value)
        editor.commit()
    }

    fun initPref(ctx: Context) {
        sharedPreferences =
            ctx.applicationContext.getSharedPreferences(PREFNAME, Context.MODE_PRIVATE)
    }

    private var sharedPreferences: SharedPreferences? = null
    
    
    fun getString(key: String?, default: String): String {
        return sharedPreferences!!.getString(key, default) ?: default
    }
    
    
    fun setString(key: String?, value: String?) {
        val editor = sharedPreferences!!.edit()
        editor.putString(key, value)
        editor.commit()
    }

    fun setStringAsync(key: String?, value: String?) {
        val editor = sharedPreferences!!.edit()
        editor.putString(key, value)
        editor.apply()
    }
}
