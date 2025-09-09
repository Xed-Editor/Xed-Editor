package com.rk.xededitor.ui.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.rk.libcommons.isDarkMode
import com.rk.libcommons.toast
import com.rk.settings.Settings
import com.rk.xededitor.ui.screens.settings.theme.themes
import java.util.Properties

data class Theme(val id: String,val name: String, val lightScheme: ColorScheme, val darkScheme: ColorScheme,val lightTerminalColors: Properties,val darkTerminalColors: Properties)

val currentTheme = mutableStateOf<Theme?>(null)
val dynamicTheme = mutableStateOf(Settings.monet)
val amoled = mutableStateOf(Settings.amoled)

@Composable
fun KarbonTheme(
    darkTheme: Boolean = when (Settings.default_night_mode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> isDarkMode(LocalContext.current)
    },
    highContrastDarkTheme: Boolean = amoled.value,
    dynamicColor: Boolean = dynamicTheme.value,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && supportsDynamicTheming()){
        val context = LocalContext.current
        when {
            darkTheme && highContrastDarkTheme ->
                dynamicDarkColorScheme(context)
                    .copy(background = Color.Black, surface = Color.Black, surfaceDim = Color.Black)
            darkTheme -> dynamicDarkColorScheme(context)
            else -> dynamicLightColorScheme(context)
        }
    }else{
        if (currentTheme.value == null) {
            currentTheme.value = themes.find { it.id == Settings.theme } ?: blueberry
        }

        val theme = if (darkTheme) {
            if (highContrastDarkTheme) {
                currentTheme.value!!.darkScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceDim = Color.Black
                )
            } else {
                currentTheme.value!!.darkScheme
            }
        } else {
            currentTheme.value!!.lightScheme
        }


        if (currentTheme.value == null){
            LaunchedEffect(theme) {
                toast("No theme selected")
            }
            if (darkTheme){
                blueberry.darkScheme
            }else{
                blueberry.lightScheme
            }
        }else{
            theme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
