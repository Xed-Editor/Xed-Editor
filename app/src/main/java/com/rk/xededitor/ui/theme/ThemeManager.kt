package com.rk.xededitor.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Resources

import com.google.android.material.color.DynamicColors

import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData

/**
* A Basic Helper for Apply correct theme in app
* @author Aquiles Trindade (trindadedev).
*/

object ThemeManager {
    
    /**
    * Function that applies the theme
    * Checks if the user is in the dark theme, and if they have the oled theme enabled, if so, applies the oled theme.
    * Checks if the user has the "Dynamic Colors" function activated, if so, applies Dynamic Colors.
    * @param activity, An instance of an Activity.
    */
    fun apply(activity: Activity) {
        if (SettingsData.isMonet()) {
             DynamicColors.applyToActivityIfAvailable(activity)
        }
        if (SettingsData.isDarkMode(activity) && SettingsData.isOled()) {
             activity.setTheme(R.style.Theme_Karbon_Oled)
        }
    }
    
    /**
    * @returns Return a current theme.
    */
    fun getCurrentTheme(ctx: Context): Resources.Theme? = ctx.theme
}