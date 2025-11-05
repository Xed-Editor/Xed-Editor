package com.rk.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
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
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

suspend fun installFromFile(file: FileObject) {
    loadConfigFromJson(file)?.installTheme()
}

suspend fun loadConfigFromJson(file: FileObject): ThemeConfig? = withContext(Dispatchers.IO) {
    return@withContext try {
        val gson = GsonBuilder()
            .excludeFieldsWithModifiers(java.lang.reflect.Modifier.STATIC)
            .create()
        gson.fromJson(file.readText(), ThemeConfig::class.java)
    } catch (e: Exception) {
        errorDialog(e)
        null
    }
}

suspend fun ThemeConfig.installTheme() = withContext(Dispatchers.IO) {
    if (id == null) {
        toast("Please specify a theme ID.")
        return@withContext
    }

    if (name == null) {
        toast("Please specify a theme name.")
        return@withContext
    }

    if (targetVersion == null) {
        toast("Please specify a valid targetVersion.")
        return@withContext
    }

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

    val lightTokenColors = light?.tokenColors.toTokenColorArray()
    val darkTokenColors = dark?.tokenColors.toTokenColorArray()

    return ThemeHolder(
        id = id!!,
        name = name!!,
        inheritBase = useTokenFallback ?: true,
        lightScheme = light?.build(isDarkTheme = false) ?: blueberry.lightScheme,
        darkScheme = dark?.build(isDarkTheme = true) ?: blueberry.darkScheme,
        lightTerminalColors = light?.terminalColors?.toProperties() ?: Properties(),
        darkTerminalColors = dark?.terminalColors?.toProperties() ?: Properties(),
        lightEditorColors = mapEditorColorScheme(light?.editorColors),
        darkEditorColors = mapEditorColorScheme(dark?.editorColors),
        lightTokenColors = lightTokenColors,
        darkTokenColors = darkTokenColors
    )
}

/**
 * Build `tokenColors` JsonArray (which is compatible with TextMate theme format)
 * from a JsonElement (which can be a JsonObject map or JsonArray of TextMate entries)
 *
 * Return value structure:
 * ```json
 * [
 *     {
 *         "scope": "<scope>",
 *         "settings": {
 *             "foreground": "#RRGGBB"
 *         }
 *     }
 * ]
 * ```
 */
private fun JsonElement?.toTokenColorArray(): JsonArray {
    if (this == null || isJsonNull) return JsonArray()

    return when {
        isJsonArray -> this.asJsonArray
        isJsonObject -> {
            val convertedArray = JsonArray()
            for ((scope, colorHex) in asJsonObject.entrySet()) {
                if (!colorHex.isJsonPrimitive) toast("Invalid value for $scope")

                val item = JsonObject().apply {
                    addProperty("scope", scope)
                    val settings = JsonObject()
                    settings.addProperty("foreground", colorHex.asString)
                    add("settings", settings)
                }
                convertedArray.add(item)
            }
            return convertedArray
        }
        else -> JsonArray()
    }
}

fun ThemePalette.build(isDarkTheme: Boolean): ColorScheme {
    return if (isDarkTheme) {
        darkColorScheme(
            primary = baseColors?.primary?.toColor() ?: blueberry.darkScheme.primary,
            onPrimary = baseColors?.onPrimary?.toColor() ?: blueberry.darkScheme.onPrimary,
            primaryContainer = baseColors?.primaryContainer?.toColor() ?: blueberry.darkScheme.primaryContainer,
            onPrimaryContainer = baseColors?.onPrimaryContainer?.toColor() ?: blueberry.darkScheme.onPrimaryContainer,
            secondary = baseColors?.secondary?.toColor() ?: blueberry.darkScheme.secondary,
            onSecondary = baseColors?.onSecondary?.toColor() ?: blueberry.darkScheme.onSecondary,
            secondaryContainer = baseColors?.secondaryContainer?.toColor() ?: blueberry.darkScheme.secondaryContainer,
            onSecondaryContainer = baseColors?.onSecondaryContainer?.toColor() ?: blueberry.darkScheme.onSecondaryContainer,
            tertiary = baseColors?.tertiary?.toColor() ?: blueberry.darkScheme.tertiary,
            onTertiary = baseColors?.onTertiary?.toColor() ?: blueberry.darkScheme.onTertiary,
            tertiaryContainer = baseColors?.tertiaryContainer?.toColor() ?: blueberry.darkScheme.tertiaryContainer,
            onTertiaryContainer = baseColors?.onTertiaryContainer?.toColor() ?: blueberry.darkScheme.onTertiaryContainer,
            error = baseColors?.error?.toColor() ?: blueberry.darkScheme.error,
            onError = baseColors?.onError?.toColor() ?: blueberry.darkScheme.onError,
            errorContainer = baseColors?.errorContainer?.toColor() ?: blueberry.darkScheme.errorContainer,
            onErrorContainer = baseColors?.onErrorContainer?.toColor() ?: blueberry.darkScheme.onErrorContainer,
            background = baseColors?.background?.toColor() ?: blueberry.darkScheme.background,
            onBackground = baseColors?.onBackground?.toColor() ?: blueberry.darkScheme.onBackground,
            surface = baseColors?.surface?.toColor() ?: blueberry.darkScheme.surface,
            onSurface = baseColors?.onSurface?.toColor() ?: blueberry.darkScheme.onSurface,
            surfaceVariant = baseColors?.surfaceVariant?.toColor() ?: blueberry.darkScheme.surfaceVariant,
            onSurfaceVariant = baseColors?.onSurfaceVariant?.toColor() ?: blueberry.darkScheme.onSurfaceVariant,
            outline = baseColors?.outline?.toColor() ?: blueberry.darkScheme.outline,
            outlineVariant = baseColors?.outlineVariant?.toColor() ?: blueberry.darkScheme.outlineVariant,
            scrim = baseColors?.scrim?.toColor() ?: blueberry.darkScheme.scrim,
            inverseSurface = baseColors?.inverseSurface?.toColor() ?: blueberry.darkScheme.inverseSurface,
            inverseOnSurface = baseColors?.inverseOnSurface?.toColor() ?: blueberry.darkScheme.inverseOnSurface,
            inversePrimary = baseColors?.inversePrimary?.toColor() ?: blueberry.darkScheme.inversePrimary,
            surfaceTint = baseColors?.surfaceTint?.toColor() ?: blueberry.darkScheme.surfaceTint,
            surfaceDim = baseColors?.surfaceDim?.toColor() ?: blueberry.darkScheme.surfaceDim,
            surfaceBright = baseColors?.surfaceBright?.toColor() ?: blueberry.darkScheme.surfaceBright,
            surfaceContainerLowest = baseColors?.surfaceContainerLowest?.toColor() ?: blueberry.darkScheme.surfaceContainerLowest,
            surfaceContainerLow = baseColors?.surfaceContainerLow?.toColor() ?: blueberry.darkScheme.surfaceContainerLow,
            surfaceContainer = baseColors?.surfaceContainer?.toColor() ?: blueberry.darkScheme.surfaceContainer,
            surfaceContainerHigh = baseColors?.surfaceContainerHigh?.toColor() ?: blueberry.darkScheme.surfaceContainerHigh,
            surfaceContainerHighest = baseColors?.surfaceContainerHighest?.toColor() ?: blueberry.darkScheme.surfaceContainerHighest
        )
    } else {
        lightColorScheme(
            primary = baseColors?.primary?.toColor() ?: blueberry.lightScheme.primary,
            onPrimary = baseColors?.onPrimary?.toColor() ?: blueberry.lightScheme.onPrimary,
            primaryContainer = baseColors?.primaryContainer?.toColor() ?: blueberry.lightScheme.primaryContainer,
            onPrimaryContainer = baseColors?.onPrimaryContainer?.toColor() ?: blueberry.lightScheme.onPrimaryContainer,
            secondary = baseColors?.secondary?.toColor() ?: blueberry.lightScheme.secondary,
            onSecondary = baseColors?.onSecondary?.toColor() ?: blueberry.lightScheme.onSecondary,
            secondaryContainer = baseColors?.secondaryContainer?.toColor() ?: blueberry.lightScheme.secondaryContainer,
            onSecondaryContainer = baseColors?.onSecondaryContainer?.toColor() ?: blueberry.lightScheme.onSecondaryContainer,
            tertiary = baseColors?.tertiary?.toColor() ?: blueberry.lightScheme.tertiary,
            onTertiary = baseColors?.onTertiary?.toColor() ?: blueberry.lightScheme.onTertiary,
            tertiaryContainer = baseColors?.tertiaryContainer?.toColor() ?: blueberry.lightScheme.tertiaryContainer,
            onTertiaryContainer = baseColors?.onTertiaryContainer?.toColor() ?: blueberry.lightScheme.onTertiaryContainer,
            error = baseColors?.error?.toColor() ?: blueberry.lightScheme.error,
            onError = baseColors?.onError?.toColor() ?: blueberry.lightScheme.onError,
            errorContainer = baseColors?.errorContainer?.toColor() ?: blueberry.lightScheme.errorContainer,
            onErrorContainer = baseColors?.onErrorContainer?.toColor() ?: blueberry.lightScheme.onErrorContainer,
            background = baseColors?.background?.toColor() ?: blueberry.lightScheme.background,
            onBackground = baseColors?.onBackground?.toColor() ?: blueberry.lightScheme.onBackground,
            surface = baseColors?.surface?.toColor() ?: blueberry.lightScheme.surface,
            onSurface = baseColors?.onSurface?.toColor() ?: blueberry.lightScheme.onSurface,
            surfaceVariant = baseColors?.surfaceVariant?.toColor() ?: blueberry.lightScheme.surfaceVariant,
            onSurfaceVariant = baseColors?.onSurfaceVariant?.toColor() ?: blueberry.lightScheme.onSurfaceVariant,
            outline = baseColors?.outline?.toColor() ?: blueberry.lightScheme.outline,
            outlineVariant = baseColors?.outlineVariant?.toColor() ?: blueberry.lightScheme.outlineVariant,
            scrim = baseColors?.scrim?.toColor() ?: blueberry.lightScheme.scrim,
            inverseSurface = baseColors?.inverseSurface?.toColor() ?: blueberry.lightScheme.inverseSurface,
            inverseOnSurface = baseColors?.inverseOnSurface?.toColor() ?: blueberry.lightScheme.inverseOnSurface,
            inversePrimary = baseColors?.inversePrimary?.toColor() ?: blueberry.lightScheme.inversePrimary,
            surfaceTint = baseColors?.surfaceTint?.toColor() ?: blueberry.lightScheme.surfaceTint,
            surfaceDim = baseColors?.surfaceDim?.toColor() ?: blueberry.lightScheme.surfaceDim,
            surfaceBright = baseColors?.surfaceBright?.toColor() ?: blueberry.lightScheme.surfaceBright,
            surfaceContainerLowest = baseColors?.surfaceContainerLowest?.toColor() ?: blueberry.lightScheme.surfaceContainerLowest,
            surfaceContainerLow = baseColors?.surfaceContainerLow?.toColor() ?: blueberry.lightScheme.surfaceContainerLow,
            surfaceContainer = baseColors?.surfaceContainer?.toColor() ?: blueberry.lightScheme.surfaceContainer,
            surfaceContainerHigh = baseColors?.surfaceContainerHigh?.toColor() ?: blueberry.lightScheme.surfaceContainerHigh,
            surfaceContainerHighest = baseColors?.surfaceContainerHighest?.toColor() ?: blueberry.lightScheme.surfaceContainerHighest
        )
    }
}
