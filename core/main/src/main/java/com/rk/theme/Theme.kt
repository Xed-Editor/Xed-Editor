package com.rk.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.MaterialColors
import com.rk.settings.Settings
import com.rk.settings.theme.themes
import com.rk.utils.isDarkTheme
import com.rk.utils.toast

val currentTheme = mutableStateOf<ThemeHolder?>(null)
val dynamicTheme = mutableStateOf(Settings.monet)
val amoled = mutableStateOf(Settings.amoled)

val LocalThemeHolder = staticCompositionLocalOf<ThemeHolder> { error("No ThemeHolder state provided") }

@Composable
fun XedTheme(
    darkTheme: Boolean = isDarkTheme(LocalContext.current),
    highContrastDarkTheme: Boolean = amoled.value,
    dynamicColor: Boolean = dynamicTheme.value,
    content: @Composable () -> Unit,
) {
    var themeHolder = blueberry
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
            if (currentTheme.value == null) {
                themeHolder = themes.find { it.id == Settings.theme } ?: themeHolder
                currentTheme.value = themeHolder
            } else {
                themeHolder = currentTheme.value ?: themeHolder
            }

            val theme =
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

            // Is possible?
            if (currentTheme.value == null) {
                LaunchedEffect(theme) { toast("No theme selected") }
                if (darkTheme) {
                    blueberry.darkScheme
                } else {
                    blueberry.lightScheme
                }
            } else {
                theme
            }
        }

    CompositionLocalProvider(LocalThemeHolder provides themeHolder) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography) {
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

val ColorScheme.folderSurface: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFFFFC857)) else Color(harmonize(0xFFFAB72D))

// Git change colors
val ColorScheme.gitAdded: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFF81C784)) else Color(harmonize(0xFF2E7D32))

val ColorScheme.gitModified: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFF64B5F6)) else Color(harmonize(0xFF1565C0))

val ColorScheme.gitDeleted: Color
    get() = this.onSurface.copy(alpha = 0.6f)

val ColorScheme.gitConflicted: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFFE57373)) else Color(harmonize(0xFFC62828))
