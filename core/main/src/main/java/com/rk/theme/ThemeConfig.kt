package com.rk.theme

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serial
import java.io.Serializable

data class ThemePalette(
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
     * */
    @Transient
    var tokenColors: JsonElement? = null
) : Serializable {
    @Serial
    private fun writeObject(out: ObjectOutputStream) {
        out.defaultWriteObject()
        out.writeObject(tokenColors?.toString())
    }

    @Serial
    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        val tokenColorsStr = input.readObject() as? String
        tokenColors = tokenColorsStr?.let { JsonParser.parseString(it) }
    }
}

data class ThemeConfig(
    val id: String,
    val name: String,
    val useTokenFallback: Boolean?,
    val light: ThemePalette,
    val dark: ThemePalette,
) : Serializable
