package com.rk.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors
import com.rk.App.Companion.themeManager
import com.rk.settings.Settings
import com.rk.settings.editor.rememberAppTypography
import com.rk.utils.isDarkTheme

val currentTheme = derivedStateOf {
     themeManager.loadedThemes.find { it.id == Settings.theme } ?: blueberry
}

val LocalThemeHolder = staticCompositionLocalOf<ThemeHolder> { error("No ThemeHolder state provided") }

@Composable
fun XedTheme(
    darkTheme: Boolean = isDarkTheme(LocalContext.current),
    highContrastDarkTheme: Boolean = Settings.amoled,
    dynamicColor: Boolean = Settings.monet,
    content: @Composable () -> Unit,
) {
    var themeHolder: ThemeHolder
    val colorScheme =
        if (dynamicColor && supportsDynamicTheming()) {
            val context = LocalContext.current
            val baseColorScheme =
                when {
                    darkTheme && highContrastDarkTheme ->
                        dynamicDarkColorScheme(context)
                            .copy(background = Color.Black, surface = Color.Black, surfaceDim = Color.Black)

                    darkTheme -> dynamicDarkColorScheme(context)
                    else -> dynamicLightColorScheme(context)
                }

            // Use default theme
            themeHolder = blueberry

            baseColorScheme
        } else {
            themeHolder = currentTheme.value

            if (darkTheme) {
                if (highContrastDarkTheme) {
                    themeHolder.darkScheme.copy(
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceDim = Color.Black,
                    )
                } else {
                    themeHolder.darkScheme
                }
            } else {
                themeHolder.lightScheme
            }
        }

    CompositionLocalProvider(LocalThemeHolder provides themeHolder) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = rememberAppTypography(LocalContext.current),
            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp)),
        ) {
            Surface(color = MaterialTheme.colorScheme.background) { content() }
        }
    }
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun harmonize(color: Long): Int {
    val context = LocalContext.current
    return MaterialColors.harmonizeWithPrimary(context, color.toInt())
}

// Custom warning colors
val ColorScheme.warningSurface: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFF633F00)) else Color(harmonize(0xFFFFDDB4))

val ColorScheme.onWarningSurface: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFFFFDDB4)) else Color(harmonize(0xFF633F00))

// Status colors
val ColorScheme.greenStatus: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFFA6DA95)) else Color(harmonize(0xFF44842E))

val ColorScheme.yellowStatus: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFFFFE082)) else Color(harmonize(0xFFE6AC00))

// Git change colors
val ColorScheme.gitAdded: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFF81C784)) else Color(harmonize(0xFF2E7D32))

val ColorScheme.gitModified: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFF64B5F6)) else Color(harmonize(0xFF1565C0))

val ColorScheme.gitDeleted: Color
    get() = this.onSurface.copy(alpha = 0.6f)

val ColorScheme.gitConflicted: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFFE57373)) else Color(harmonize(0xFFC62828))
