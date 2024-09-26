package com.rk.xededitor.ui.theme

import android.content.Context
import android.content.res.Resources
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData

object ThemeManager {
    
    fun apply(context: Context) {
        if (SettingData.isDarkMode(context) && SettingData.isOled()) {
             context.setTheme(R.style.Theme_Karbon_Oled)
             return
        }
    }
    
    fun getCurrentTheme(context: Context): Resources.Theme? = return context.theme
}
