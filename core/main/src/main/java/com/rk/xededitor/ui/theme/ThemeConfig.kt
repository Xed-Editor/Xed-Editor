package com.rk.xededitor.ui.theme

import java.io.Serializable

data class ThemePalette(
    val primary: String? = null,
    val onPrimary: String? = null,
    val primaryContainer: String? = null,
    val onPrimaryContainer: String? = null,
    val secondary: String? = null,
    val onSecondary: String? = null,
    val secondaryContainer: String? = null,
    val onSecondaryContainer: String? = null,
    val tertiary: String? = null,
    val onTertiary: String? = null,
    val tertiaryContainer: String? = null,
    val onTertiaryContainer: String? = null,
    val error: String? = null,
    val errorContainer: String? = null,
    val onError: String? = null,
    val onErrorContainer: String? = null,
    val background: String? = null,
    val onBackground: String? = null,
    val surface: String? = null,
    val onSurface: String? = null,
    val surfaceVariant: String? = null,
    val onSurfaceVariant: String? = null,
    val outline: String? = null,
    val inverseOnSurface: String? = null,
    val inverseSurface: String? = null,
    val inversePrimary: String? = null,
    val surfaceTint: String? = null,
    val outlineVariant: String? = null,
    val scrim: String? = null

) : Serializable

data class ThemeConfig(
    val id: String,
    val name: String,
    val light: ThemePalette,
    val dark: ThemePalette
) : Serializable
