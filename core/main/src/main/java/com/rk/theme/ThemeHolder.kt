package com.rk.theme

import androidx.compose.material3.ColorScheme
import java.util.Properties

data class ThemeHolder(
    val id: String,
    val name: String,
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme,
    val lightTerminalColors: Properties,
    val darkTerminalColors: Properties,
    val lightEditorColors: List<EditorColor>,
    val darkEditorColors: List<EditorColor>
)