package com.rk.editor

import android.content.Context
import com.rk.App.Companion.themeManager
import com.rk.settings.Settings
import com.rk.utils.isDarkTheme

const val TEXTMATE_PREFIX = "textmate/"
const val TEXTMATE_AMOLED_PREFIX = "textmate/black/"
const val DARCULA_THEME = "darcula.json"
const val QUIETLIGHT_THEME = "quietlight.json"
const val LANGUAGES_FILE = "languages.json"
const val KEYWORDS_FILE = "keywords.json"

fun getCacheKey(context: Context): String {
    val darkTheme = isDarkTheme(context)
    val prefix = if (darkTheme) "dark" else "light"

    return buildString {
        append(prefix)
        append('_')
        append(Settings.theme)
        append('_')
        append(themeManager.currentTheme.darkEditorColors.hashCode())
        append('_')
        append(themeManager.currentTheme.lightEditorColors.hashCode())
        append('_')
        append(themeManager.currentTheme.darkTokenColors.hashCode())
        append('_')
        append(themeManager.currentTheme.lightTokenColors.hashCode())
        append('_')
        append(Settings.amoled)
        append('_')
        append(Settings.monet)
    }
}
