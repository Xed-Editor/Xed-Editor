package com.rk.xededitor.ui.theme

import android.content.Context
import android.content.res.Resources

import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData

/*
* A Basic Helper for Apply correct theme in app
* @author Aquiles Trindade (trindadedev).
*/

object ThemeManager {
    
    fun apply(context: Context) {
        if (SettingsData.isDarkMode(context) && SettingsData.isOled()) {
             context.setTheme(R.style.Theme_Karbon_Oled)
             return
        }
    }
    
    fun getCurrentTheme(context: Context): Resources.Theme? = return context.theme
}