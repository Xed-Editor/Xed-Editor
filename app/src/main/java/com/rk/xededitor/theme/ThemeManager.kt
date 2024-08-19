package com.rk.xededitor.theme

import android.content.Context
import android.content.res.Resources
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData

object ThemeManager {
    private const val THEME_PREFIX = "selectable_"

    fun getSelectedTheme(): String {
        return SettingsData.getString(SettingsData.Keys.SELECTED_THEME, "Berry")
    }

    fun setSelectedTheme(themeName: String) {
        SettingsData.setString(SettingsData.Keys.SELECTED_THEME, themeName)
    }

    fun applyTheme(context: Context) {
        setTheme(context, getSelectedTheme())
    }

    fun setTheme(context: Context, themeName: String) {
        context.setTheme(getThemeIdByName(context, themeName))
        setSelectedTheme(themeName)
    }

    private fun getThemeIdByName(context: Context, themeName: String): Int {
        val themeResName = "$THEME_PREFIX$themeName"
        return context.resources.getIdentifier(themeResName, "style", context.packageName)
    }

    fun getThemes(context: Context): List<Pair<String, Int>> {
        val stylesClass = R.style::class.java
        val fields = stylesClass.declaredFields
        val themes = mutableListOf<Pair<String, Int>>()

        for (field in fields) {
            try {
                val resourceId = field.getInt(null)
                val resourceName = context.resources.getResourceEntryName(resourceId)
                if (!resourceName.startsWith(THEME_PREFIX)) {
                    continue
                }
                val finalName = if (resourceName.removePrefix(THEME_PREFIX) == "Berry") {
                    "Berry (Default)"
                } else {
                    resourceName.removePrefix(THEME_PREFIX)
                }
                themes.add(Pair(finalName, resourceId))
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }

        return themes
    }

    fun getCurrentTheme(context: Context): Resources.Theme? {
        return context.theme
    }

    fun getCurrentThemeId(context: Context): Int {
        val attrs = intArrayOf(android.R.attr.theme)
        val typedArray = getCurrentTheme(context)!!.obtainStyledAttributes(attrs)
        val themeId = typedArray.getResourceId(0, 0)
        typedArray.recycle()
        return themeId
    }
}
