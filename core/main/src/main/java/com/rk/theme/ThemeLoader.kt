package com.rk.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.file.themeDir
import com.rk.settings.theme.themes
import com.rk.utils.errorDialog
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Properties
import androidx.core.graphics.toColorInt


suspend fun installFromFile(file: FileObject) {
    loadConfigFromJson(file)?.installTheme()
}

suspend fun loadConfigFromJson(file: FileObject): ThemeConfig? = withContext(Dispatchers.IO) {
    return@withContext try {
        val gson = Gson()
        gson.fromJson(file.readText(), ThemeConfig::class.java)
    } catch (e: Exception) {
        errorDialog(e)
        null
    }
}

suspend fun ThemeConfig.installTheme() = withContext(Dispatchers.IO) {
    val themeFile = themeDir().child(this@installTheme.name)
    ObjectOutputStream(FileOutputStream(themeFile)).use { out ->
        out.writeObject(this@installTheme)
    }
}

fun updateThemes() {
    themes.clear()
    themes.addAll(inbuiltThemes)
    loadThemes()
}

fun loadThemes() {
    val themeDir = themeDir()
    if (!themeDir.exists()) {
        return
    }

    themeDir.listFiles()?.forEach { file ->
        runCatching {
            ObjectInputStream(FileInputStream(file)).use { input ->
                val config = input.readObject() as? ThemeConfig
                if (config != null) {
                    themes.add(config.build())
                }

            }
        }.onFailure {
            it.printStackTrace()
            file.delete()
        }
    }
}

private fun String.toColor(): Color {
    return try {
        Color(this.toColorInt())
    } catch (e: Exception) {
        toast("Invalid color: $this")
        Color.Unspecified
    }
}

fun ThemeConfig.build(): ThemeHolder {
    fun Map<String, String>.toProperties(): Properties {
        val props = Properties()
        for ((k, v) in this) props[k] = v
        return props
    }

    return ThemeHolder(
        id = id,
        name = name,
        lightScheme = this.light.build(isDarkTheme = false),
        darkScheme = this.dark.build(isDarkTheme = true),
        lightTerminalColors = light.terminalColors?.toProperties() ?: Properties(),
        darkTerminalColors = dark.terminalColors?.toProperties() ?: Properties(),
        lightEditorColors = mapEditorColorScheme(this.light.editorColors),
        darkEditorColors = mapEditorColorScheme(this.dark.editorColors)
    )

}

fun ThemePalette.build(isDarkTheme: Boolean): ColorScheme {
    return if (isDarkTheme) {
        darkColorScheme(
            primary = primary?.toColor() ?: blueberry.darkScheme.primary,
            onPrimary = onPrimary?.toColor() ?: blueberry.darkScheme.onPrimary,
            primaryContainer = primaryContainer?.toColor() ?: blueberry.darkScheme.primaryContainer,
            onPrimaryContainer = onPrimaryContainer?.toColor()
                ?: blueberry.darkScheme.onPrimaryContainer,
            secondary = secondary?.toColor() ?: blueberry.darkScheme.secondary,
            onSecondary = onSecondary?.toColor() ?: blueberry.darkScheme.onSecondary,
            secondaryContainer = secondaryContainer?.toColor()
                ?: blueberry.darkScheme.secondaryContainer,
            onSecondaryContainer = onSecondaryContainer?.toColor()
                ?: blueberry.darkScheme.onSecondaryContainer,
            tertiary = tertiary?.toColor() ?: blueberry.darkScheme.tertiary,
            onTertiary = onTertiary?.toColor() ?: blueberry.darkScheme.onTertiary,
            tertiaryContainer = tertiaryContainer?.toColor()
                ?: blueberry.darkScheme.tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer?.toColor()
                ?: blueberry.darkScheme.onTertiaryContainer,
            error = error?.toColor() ?: blueberry.darkScheme.error,
            errorContainer = errorContainer?.toColor() ?: blueberry.darkScheme.errorContainer,
            onError = onError?.toColor() ?: blueberry.darkScheme.onError,
            onErrorContainer = onErrorContainer?.toColor() ?: blueberry.darkScheme.onErrorContainer,
            background = background?.toColor() ?: blueberry.darkScheme.background,
            onBackground = onBackground?.toColor() ?: blueberry.darkScheme.onBackground,
            surface = surface?.toColor() ?: blueberry.darkScheme.surface,
            onSurface = onSurface?.toColor() ?: blueberry.darkScheme.onSurface,
            surfaceVariant = surfaceVariant?.toColor() ?: blueberry.darkScheme.surfaceVariant,
            onSurfaceVariant = onSurfaceVariant?.toColor() ?: blueberry.darkScheme.onSurfaceVariant,
            outline = outline?.toColor() ?: blueberry.darkScheme.outline,
            inverseOnSurface = inverseOnSurface?.toColor() ?: blueberry.darkScheme.inverseOnSurface,
            inverseSurface = inverseSurface?.toColor() ?: blueberry.darkScheme.inverseSurface,
            inversePrimary = inversePrimary?.toColor() ?: blueberry.darkScheme.inversePrimary,
            surfaceTint = surfaceTint?.toColor() ?: blueberry.darkScheme.surfaceTint,
            outlineVariant = outlineVariant?.toColor() ?: blueberry.darkScheme.outlineVariant,
            scrim = scrim?.toColor() ?: blueberry.darkScheme.scrim
        )
    } else {
        lightColorScheme(
            primary = primary?.toColor() ?: blueberry.lightScheme.primary,
            onPrimary = onPrimary?.toColor() ?: blueberry.lightScheme.onPrimary,
            primaryContainer = primaryContainer?.toColor()
                ?: blueberry.lightScheme.primaryContainer,
            onPrimaryContainer = onPrimaryContainer?.toColor()
                ?: blueberry.lightScheme.onPrimaryContainer,
            secondary = secondary?.toColor() ?: blueberry.lightScheme.secondary,
            onSecondary = onSecondary?.toColor() ?: blueberry.lightScheme.onSecondary,
            secondaryContainer = secondaryContainer?.toColor()
                ?: blueberry.lightScheme.secondaryContainer,
            onSecondaryContainer = onSecondaryContainer?.toColor()
                ?: blueberry.lightScheme.onSecondaryContainer,
            tertiary = tertiary?.toColor() ?: blueberry.lightScheme.tertiary,
            onTertiary = onTertiary?.toColor() ?: blueberry.lightScheme.onTertiary,
            tertiaryContainer = tertiaryContainer?.toColor()
                ?: blueberry.lightScheme.tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer?.toColor()
                ?: blueberry.lightScheme.onTertiaryContainer,
            error = error?.toColor() ?: blueberry.lightScheme.error,
            errorContainer = errorContainer?.toColor() ?: blueberry.lightScheme.errorContainer,
            onError = onError?.toColor() ?: blueberry.lightScheme.onError,
            onErrorContainer = onErrorContainer?.toColor()
                ?: blueberry.lightScheme.onErrorContainer,
            background = background?.toColor() ?: blueberry.lightScheme.background,
            onBackground = onBackground?.toColor() ?: blueberry.lightScheme.onBackground,
            surface = surface?.toColor() ?: blueberry.lightScheme.surface,
            onSurface = onSurface?.toColor() ?: blueberry.lightScheme.onSurface,
            surfaceVariant = surfaceVariant?.toColor() ?: blueberry.lightScheme.surfaceVariant,
            onSurfaceVariant = onSurfaceVariant?.toColor()
                ?: blueberry.lightScheme.onSurfaceVariant,
            outline = outline?.toColor() ?: blueberry.lightScheme.outline,
            inverseOnSurface = inverseOnSurface?.toColor()
                ?: blueberry.lightScheme.inverseOnSurface,
            inverseSurface = inverseSurface?.toColor() ?: blueberry.lightScheme.inverseSurface,
            inversePrimary = inversePrimary?.toColor() ?: blueberry.lightScheme.inversePrimary,
            surfaceTint = surfaceTint?.toColor() ?: blueberry.lightScheme.surfaceTint,
            outlineVariant = outlineVariant?.toColor() ?: blueberry.lightScheme.outlineVariant,
            scrim = scrim?.toColor() ?: blueberry.lightScheme.scrim
        )
    }

}
