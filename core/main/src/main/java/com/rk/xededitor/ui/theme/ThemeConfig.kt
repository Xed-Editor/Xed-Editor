package com.rk.xededitor.ui.theme

import java.io.Serializable

data class ThemePalette(
    val primary: String,
    val onPrimary: String,
    val primaryContainer: String,
    val onPrimaryContainer: String,
    val secondary: String,
    val onSecondary: String,
    val secondaryContainer: String,
    val onSecondaryContainer: String,
    val tertiary: String,
    val onTertiary: String,
    val tertiaryContainer: String,
    val onTertiaryContainer: String,
    val error: String,
    val errorContainer: String,
    val onError: String,
    val onErrorContainer: String,
    val background: String,
    val onBackground: String,
    val surface: String,
    val onSurface: String,
    val surfaceVariant: String,
    val onSurfaceVariant: String,
    val outline: String,
    val inverseOnSurface: String,
    val inverseSurface: String,
    val inversePrimary: String,
    val surfaceTint: String,
    val outlineVariant: String,
    val scrim: String
) : Serializable

data class ThemeConfig(
    val id: String,
    val name: String,
    val light: ThemePalette,
    val dark: ThemePalette
) : Serializable
