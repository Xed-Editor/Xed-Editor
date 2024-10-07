package com.rk.xededitor.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Resources

import androidx.appcompat.app.AppCompatDelegate

import com.google.android.material.color.DynamicColors

import com.rk.xededitor.R
import com.rk.xededitor.SettingsData
import com.rk.xededitor.Keys

/**
 * A basic helper for applying the correct theme in the app.
 * @author Aquiles Trindade (trindadedev).
 */
object ThemeManager {

    /**
     * Applies the theme based on user settings.
     * @param activity An instance of an Activity.
     */
    fun apply(activity: Activity) {
        val nightMode = SettingsData.getString(Keys.DEFAULT_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString()).toInt()
        
        // set theme mode
        if (nightMode != AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }

        // apply OLED theme if dark mode and OLED setting is enable
        if (SettingsData.isDarkMode(activity) && SettingsData.isOled()) {
            activity.setTheme(R.style.Theme_Karbon_Oled)
        }
        
        // enable monet theme
        if (SettingsData.isMonet()) {
            DynamicColors.applyToActivityIfAvailable(activity)
            activity.setTheme(R.style.Theme_Karbon_Oled)
        }
    }

    /**
     * Returns the current theme.
     * @param ctx The context from which to get the theme.
     */
    fun getCurrentTheme(ctx: Context): Resources.Theme? = ctx.theme
}