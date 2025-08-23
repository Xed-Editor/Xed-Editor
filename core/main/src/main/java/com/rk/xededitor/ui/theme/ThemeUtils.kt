package com.rk.xededitor.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.file.themeDir
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toast
import com.rk.xededitor.ui.screens.settings.theme.themes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream


suspend fun installFromFile(file: FileObject){
    loadConfigFromJson(file)?.installTheme()
}

suspend fun loadConfigFromJson(file: FileObject): ThemeConfig? = withContext(Dispatchers.IO){
    return@withContext try {
        val gson = Gson()
        gson.fromJson(file.readText(), ThemeConfig::class.java)
    } catch (e: Exception) {
        errorDialog(e)
        null
    }
}

suspend fun ThemeConfig.installTheme()= withContext(Dispatchers.IO){
    val themeFile = themeDir().child(this@installTheme.name)
    ObjectOutputStream(FileOutputStream(themeFile)).use { out ->
        out.writeObject(this@installTheme)
    }
}

fun updateThemes(){
    themes.clear()
    themes.addAll(inbuiltThemes)
    loadThemes()
}

fun loadThemes(){
    val themeDir = themeDir()
    if (!themeDir.exists()){
        return
    }

    themeDir.listFiles()?.forEach {
        ObjectInputStream(FileInputStream(it)).use { input ->
            val config = input.readObject() as? ThemeConfig
            if (config != null){
                themes.add(config.build())
            }

        }
    }
}

fun String.toColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        toast("Invalid color: $e")
        Color.Unspecified
    }
}

fun ThemeConfig.build(): Theme{
    return Theme(id = id, name = name, lightScheme = this.light.build(isDarkTheme = false), darkScheme = this.dark.build(isDarkTheme = true))
}

fun ThemePalette.build(isDarkTheme: Boolean): ColorScheme {
    return if (isDarkTheme){
        darkColorScheme(
            primary = primary.toColor(),
            onPrimary = onPrimary.toColor(),
            primaryContainer = primaryContainer.toColor(),
            onPrimaryContainer = onPrimaryContainer.toColor(),
            secondary = secondary.toColor(),
            onSecondary = onSecondary.toColor(),
            secondaryContainer = secondaryContainer.toColor(),
            onSecondaryContainer = onSecondaryContainer.toColor(),
            tertiary = tertiary.toColor(),
            onTertiary = onTertiary.toColor(),
            tertiaryContainer = tertiaryContainer.toColor(),
            onTertiaryContainer = onTertiaryContainer.toColor(),
            error = error.toColor(),
            errorContainer = errorContainer.toColor(),
            onError = onError.toColor(),
            onErrorContainer = onErrorContainer.toColor(),
            background = background.toColor(),
            onBackground = onBackground.toColor(),
            surface = surface.toColor(),
            onSurface = onSurface.toColor(),
            surfaceVariant = surfaceVariant.toColor(),
            onSurfaceVariant = onSurfaceVariant.toColor(),
            outline = outline.toColor(),
            inverseOnSurface = inverseOnSurface.toColor(),
            inverseSurface = inverseSurface.toColor(),
            inversePrimary = inversePrimary.toColor(),
            surfaceTint = surfaceTint.toColor(),
            outlineVariant = outlineVariant.toColor(),
            scrim = scrim.toColor())
    }else{
        lightColorScheme(
            primary = primary.toColor(),
            onPrimary = onPrimary.toColor(),
            primaryContainer = primaryContainer.toColor(),
            onPrimaryContainer = onPrimaryContainer.toColor(),
            secondary = secondary.toColor(),
            onSecondary = onSecondary.toColor(),
            secondaryContainer = secondaryContainer.toColor(),
            onSecondaryContainer = onSecondaryContainer.toColor(),
            tertiary = tertiary.toColor(),
            onTertiary = onTertiary.toColor(),
            tertiaryContainer = tertiaryContainer.toColor(),
            onTertiaryContainer = onTertiaryContainer.toColor(),
            error = error.toColor(),
            errorContainer = errorContainer.toColor(),
            onError = onError.toColor(),
            onErrorContainer = onErrorContainer.toColor(),
            background = background.toColor(),
            onBackground = onBackground.toColor(),
            surface = surface.toColor(),
            onSurface = onSurface.toColor(),
            surfaceVariant = surfaceVariant.toColor(),
            onSurfaceVariant = onSurfaceVariant.toColor(),
            outline = outline.toColor(),
            inverseOnSurface = inverseOnSurface.toColor(),
            inverseSurface = inverseSurface.toColor(),
            inversePrimary = inversePrimary.toColor(),
            surfaceTint = surfaceTint.toColor(),
            outlineVariant = outlineVariant.toColor(),
            scrim = scrim.toColor())
    }

}