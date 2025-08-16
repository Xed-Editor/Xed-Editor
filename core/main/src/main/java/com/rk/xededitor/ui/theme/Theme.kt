package com.rk.xededitor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.rk.libcommons.isDarkMode
import com.rk.libcommons.toast
import com.rk.settings.Settings
import com.rk.xededitor.ui.screens.settings.theme.themes

data class Theme(val id: String,val name: String, val lightScheme: ColorScheme, val darkScheme: ColorScheme)

val currentTheme = mutableStateOf<Theme?>(null)
val dynamicTheme = mutableStateOf(Settings.monet)

@Composable
fun KarbonTheme(
    darkTheme: Boolean = when (Settings.default_night_mode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> isDarkMode(LocalContext.current)
    },
    highContrastDarkTheme: Boolean = Settings.amoled,
    dynamicColor: Boolean = dynamicTheme.value,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && supportsDynamicTheming()){
        val context = LocalContext.current
        when {
            darkTheme && highContrastDarkTheme ->
                dynamicDarkColorScheme(context)
                    .copy(background = Color.Black, surface = Color.Black)
            darkTheme -> dynamicDarkColorScheme(context)
            else -> dynamicLightColorScheme(context)
        }
    }else{
        if (currentTheme.value == null){
            currentTheme.value = themes.find { it.id == Settings.theme }
        }

        val theme = if (darkTheme){
            if (highContrastDarkTheme){
                currentTheme.value?.darkScheme?.copy(background = Color.Black, surface = Color.Black)
            }else{
                currentTheme.value?.darkScheme
            }
        }else{
            currentTheme.value?.lightScheme
        }

        if (theme == null){
            LaunchedEffect(theme) {
                toast("No theme selected")
            }
            if (darkTheme){
                defaultTheme.darkScheme
            }else{
                defaultTheme.lightScheme
            }
        }else{
            theme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
