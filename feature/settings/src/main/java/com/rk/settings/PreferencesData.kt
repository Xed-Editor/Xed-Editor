package com.rk.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.rk.libcommons.application


@SuppressLint("ApplySharedPref")
object PreferencesData {
    init {
        PreferencesData.initPref(application!!)
    }
    private const val PREFNAME = "Settings"

    fun isDarkMode(ctx: Context): Boolean {
        val mode =
            getString(
                PreferencesKeys.DEFAULT_NIGHT_MODE,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString(),
            )
        return when (mode) {
            AppCompatDelegate.MODE_NIGHT_YES.toString() -> {
                true
            }
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString() -> {
                ((ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES)
            }
            else -> {
                false
            }
        }
    }

    fun isOled(): Boolean {
        return getBoolean(PreferencesKeys.OLED, false)
    }

    fun isMonet(): Boolean {
        return getBoolean(PreferencesKeys.MONET, false)
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
}
