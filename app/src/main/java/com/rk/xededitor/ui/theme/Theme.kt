package com.rk.xededitor.ui.theme
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rk.xededitor.Settings.SettingsData

private val DarkColorScheme = darkColorScheme(
  primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80
)

//todo
private val AmoledColorScheme = darkColorScheme(
  primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
  primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40
)

@Composable
fun KarbonTheme(

  darkTheme: Boolean = SettingsData.isDarkMode(LocalContext.current),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit
) {
  val context = LocalContext.current
  val activity = context as Activity

  // Determine which color scheme to use
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      if (darkTheme) {
        dynamicDarkColorScheme(context)
      } else {
        dynamicLightColorScheme(context)
      }
    }

    darkTheme -> if (SettingsData.isOled()) {
      AmoledColorScheme
    } else {
      DarkColorScheme
    }
    else -> LightColorScheme
  }

  val window = activity.window
  val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

  windowInsetsController.isAppearanceLightStatusBars = !darkTheme

  // Apply the theme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
