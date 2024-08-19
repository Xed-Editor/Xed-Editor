package com.rk.xededitor.Settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

@SuppressLint("ApplySharedPref")
object SettingsData {
    private const val prefName = "Settings"


    object Keys{
        const val PIN_LINE_NUMBER="pinline"
        const val WORD_WRAP_ENABLED="wordwrap"
        const val TAB_SIZE="tabsize"
        const val CURSOR_ANIMATION_ENABLED="CursorAnimation"
        const val TEXT_SIZE="textsize"
        const val LAST_OPENED_PATH="lastOpenedPath"
        const val USE_SPACE_INTABS="useSpaces"
        const val SHOW_ARROW_KEYS="show_arrows"
        const val OLED="isOled"
        const val DEFAULT_NIGHT_MODE="default_night_mode"
        const val ENABLE_PLUGINS="enablePlugin"
        const val SELECTED_THEME="selected_theme"
        const val PRIVATE_DATA="privateData"
        const val THEMES="Themes"
        const val KEEP_DRAWER_LOCKED="keepdrawerlocked"
        const val DIAGONAL_SCROLL="diagnolScroll"
        const val SHOW_LINE_NUMBERS="showlinenumbers"
        const val AUTO_SAVE="auto_save"
        const val AUTO_SAVE_TIME="auto_save_time"

        //used in SettingsMainActivity
        const val APPLICATION="app_"
        const val EDITOR="editor_"
        const val PLUGINS="plugins_"
    }


    @JvmStatic
    fun isDarkMode(ctx: Context): Boolean {
        return ((ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES)
    }

    @JvmStatic
    fun isOled(): Boolean {
       return getBoolean(Keys.OLED,false)
    }

    @JvmStatic
    fun getBoolean(key: String?, default: Boolean): Boolean {
        return sharedPreferences!!.getBoolean(key,default)
    }


    @JvmStatic
    fun setBoolean(key: String?, value: Boolean) {
        val editor = sharedPreferences!!.edit()
        editor.putBoolean(key, value)
        editor.commit()
    }

    @JvmStatic
    fun initPref(ctx: Context){
        sharedPreferences = ctx.applicationContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    }

    @JvmStatic
    private var sharedPreferences:SharedPreferences? = null

    @JvmStatic
    fun getString(key: String?, default: String): String {
        return sharedPreferences!!.getString(key, default) ?: default
    }

    @JvmStatic
    fun setString(key: String?, value: String?) {
        val editor = sharedPreferences!!.edit()
        editor.putString(key, value)
        editor.commit()
    }
}
