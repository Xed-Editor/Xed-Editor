package com.rk.theme

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Keep
@Serializable
data class BaseColors(
    val primary: String? = null,
    val onPrimary: String? = null,
    val primaryContainer: String? = null,
    val onPrimaryContainer: String? = null,
    val secondary: String? = null,
    val onSecondary: String? = null,
    val secondaryContainer: String? = null,
    val onSecondaryContainer: String? = null,
    val tertiary: String? = null,
    val onTertiary: String? = null,
    val tertiaryContainer: String? = null,
    val onTertiaryContainer: String? = null,
    val error: String? = null,
    val onError: String? = null,
    val errorContainer: String? = null,
    val onErrorContainer: String? = null,
    val background: String? = null,
    val onBackground: String? = null,
    val surface: String? = null,
    val onSurface: String? = null,
    val surfaceVariant: String? = null,
    val onSurfaceVariant: String? = null,
    val outline: String? = null,
    val outlineVariant: String? = null,
    val scrim: String? = null,
    val inverseSurface: String? = null,
    val inverseOnSurface: String? = null,
    val inversePrimary: String? = null,
    val surfaceTint: String? = null,
    val surfaceDim: String? = null,
    val surfaceBright: String? = null,
    val surfaceContainerLowest: String? = null,
    val surfaceContainerLow: String? = null,
    val surfaceContainer: String? = null,
    val surfaceContainerHigh: String? = null,
    val surfaceContainerHighest: String? = null,
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 4610250133026960367L
    }
}

@Keep
@Serializable
data class ThemePaletteNew(
    val baseColors: BaseColors?,
    val terminalColors: Map<String, String>? = null,
    val editorColors: Map<String, String>? = null,
    /**
     * Can be either a JsonArray or a JsonObject.
     *
     * Option 1:
     * ```json
     * {
     *     "tokenColors": {
     *         "comment": "#FF0000",
     *         "keyword": "#00FF00",
     *         // ...
     *     }
     * }
     * ```
     *
     * Option 2 (TextMate-style):
     * ```json
     * {
     *     "tokenColors": [
     *         {
     *             "scope": "comment",
     *             "settings": {
     *                 "foreground": "#FF0000"
     *             }
     *         },
     *         {
     *             "scope": "keyword",
     *             "settings": {
     *                 "foreground": "#00FF00"
     *             }
     *         },
     *         // ...
     *     ]
     * }
     * ```
     */
    val tokenColors: JsonElement? = null,
) : java.io.Serializable

/**
 * Content of a theme's `theme.json`. Kept separate from [ThemeManifest] at all
 * times: `manifest.json` carries package metadata only, `theme.json` carries the
 * color palettes only.
 */
@Serializable
data class ThemeFile(
    val light: ThemePaletteNew? = null,
    val dark: ThemePaletteNew? = null,
)
