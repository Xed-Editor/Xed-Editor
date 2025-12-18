package com.rk.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.toColorInt
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.rk.activities.settings.SettingsActivity
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.file.themeDir
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.theme.themes
import com.rk.utils.application
import com.rk.utils.dialog
import com.rk.utils.errorDialog
import com.rk.utils.toast
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun installFromFile(file: FileObject) {
    loadConfigFromJson(file)?.installTheme()
}

suspend fun loadConfigFromJson(file: FileObject): ThemeConfig? =
    withContext(Dispatchers.IO) {
        return@withContext try {
            val gson = GsonBuilder().excludeFieldsWithModifiers(java.lang.reflect.Modifier.STATIC).create()
            gson.fromJson(file.readText(), ThemeConfig::class.java)
        } catch (e: Exception) {
            errorDialog(e)
            null
        }
    }

suspend fun ThemeConfig.installTheme() =
    withContext(Dispatchers.IO) {
        if (id == null) {
            dialog(
                context = SettingsActivity.instance,
                title = strings.theme_install_failed.getString(),
                msg = strings.theme_id_missing.getString(),
                cancelable = false,
            )
            return@withContext
        }

        if (name == null) {
            dialog(
                context = SettingsActivity.instance,
                title = strings.theme_install_failed.getString(),
                msg = strings.theme_name_missing.getString(),
                cancelable = false,
            )
            return@withContext
        }

        if (targetVersion == null) {
            dialog(
                context = SettingsActivity.instance,
                title = strings.theme_install_failed.getString(),
                msg = strings.theme_version_missing.getString(),
                cancelable = false,
            )
            return@withContext
        }

        val packageName = application!!.packageName
        val packageManager = application!!.packageManager
        val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
        if (targetVersion.toLong() != currentVersionCode) {
            dialog(
                context = SettingsActivity.instance,
                title = strings.warning.getString(),
                msg = strings.incompatible_theme_warning.getString(),
                cancelString = strings.cancel,
                okString = strings.continue_action,
                onOk = {
                    finishThemeInstall(name)
                    updateThemes()
                },
            )
            return@withContext
        }

        finishThemeInstall(name)
        updateThemes()
    }

private fun ThemeConfig.finishThemeInstall(name: String) {
    val themeFile = themeDir().child(name)
    ObjectOutputStream(FileOutputStream(themeFile)).use { out -> out.writeObject(this) }
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
            }
            .onFailure {
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
        inheritBase = inheritBase ?: true,
        lightScheme = light?.build(isDarkTheme = false) ?: blueberry.lightScheme,
        darkScheme = dark?.build(isDarkTheme = true) ?: blueberry.darkScheme,
        lightTerminalColors = light?.terminalColors?.toProperties() ?: Properties(),
        darkTerminalColors = dark?.terminalColors?.toProperties() ?: Properties(),
        lightEditorColors = mapEditorColorScheme(light?.editorColors),
        darkEditorColors = mapEditorColorScheme(dark?.editorColors),
        lightTokenColors = lightTokenColors,
        darkTokenColors = darkTokenColors,
    )
}

/**
 * Build `tokenColors` JsonArray (which is compatible with TextMate theme format) from a JsonElement (which can be a
 * JsonObject map or JsonArray of TextMate entries)
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

                val item =
                    JsonObject().apply {
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
            onSecondaryContainer =
                baseColors?.onSecondaryContainer?.toColor() ?: blueberry.darkScheme.onSecondaryContainer,
            tertiary = baseColors?.tertiary?.toColor() ?: blueberry.darkScheme.tertiary,
            onTertiary = baseColors?.onTertiary?.toColor() ?: blueberry.darkScheme.onTertiary,
            tertiaryContainer = baseColors?.tertiaryContainer?.toColor() ?: blueberry.darkScheme.tertiaryContainer,
            onTertiaryContainer =
                baseColors?.onTertiaryContainer?.toColor() ?: blueberry.darkScheme.onTertiaryContainer,
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
            surfaceContainerLowest =
                baseColors?.surfaceContainerLowest?.toColor() ?: blueberry.darkScheme.surfaceContainerLowest,
            surfaceContainerLow =
                baseColors?.surfaceContainerLow?.toColor() ?: blueberry.darkScheme.surfaceContainerLow,
            surfaceContainer = baseColors?.surfaceContainer?.toColor() ?: blueberry.darkScheme.surfaceContainer,
            surfaceContainerHigh =
                baseColors?.surfaceContainerHigh?.toColor() ?: blueberry.darkScheme.surfaceContainerHigh,
            surfaceContainerHighest =
                baseColors?.surfaceContainerHighest?.toColor() ?: blueberry.darkScheme.surfaceContainerHighest,
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
            onSecondaryContainer =
                baseColors?.onSecondaryContainer?.toColor() ?: blueberry.lightScheme.onSecondaryContainer,
            tertiary = baseColors?.tertiary?.toColor() ?: blueberry.lightScheme.tertiary,
            onTertiary = baseColors?.onTertiary?.toColor() ?: blueberry.lightScheme.onTertiary,
            tertiaryContainer = baseColors?.tertiaryContainer?.toColor() ?: blueberry.lightScheme.tertiaryContainer,
            onTertiaryContainer =
                baseColors?.onTertiaryContainer?.toColor() ?: blueberry.lightScheme.onTertiaryContainer,
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
            surfaceContainerLowest =
                baseColors?.surfaceContainerLowest?.toColor() ?: blueberry.lightScheme.surfaceContainerLowest,
            surfaceContainerLow =
                baseColors?.surfaceContainerLow?.toColor() ?: blueberry.lightScheme.surfaceContainerLow,
            surfaceContainer = baseColors?.surfaceContainer?.toColor() ?: blueberry.lightScheme.surfaceContainer,
            surfaceContainerHigh =
                baseColors?.surfaceContainerHigh?.toColor() ?: blueberry.lightScheme.surfaceContainerHigh,
            surfaceContainerHighest =
                baseColors?.surfaceContainerHighest?.toColor() ?: blueberry.lightScheme.surfaceContainerHighest,
        )
    }
}
