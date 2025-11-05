package com.rk.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.gson.JsonArray
import java.util.Properties

fun Color.toHex(): String {
    val intColor = this.value.toLong().toInt()
    val r = (intColor shr 16) and 0xFF
    val g = (intColor shr 8) and 0xFF
    val b = intColor and 0xFF

    return String.format("#%02X%02X%02X", r, g, b)
}

val blueberry = ThemeHolder(
    id = "blueberry-default",
    name = "BlueBerry (Default)",
    lightScheme = lightColorScheme(
        primary = Color(0xFF445E91),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD8E2FF),
        onPrimaryContainer = Color(0xFF001A41),
        secondary = Color(0xFF575E71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDBE2F9),
        onSecondaryContainer = Color(0xFF141B2C),
        tertiary = Color(0xFF715573),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFBD7FC),
        onTertiaryContainer = Color(0xFF29132D),
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF1A1B20),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF1A1B20),
        surfaceVariant = Color(0xFFE1E2EC),
        onSurfaceVariant = Color(0xFF44474F),
        outline = Color(0xFF74777F),
        inverseOnSurface = Color(0xFFF0F0F7),
        inverseSurface = Color(0xFF2F3036),
        inversePrimary = Color(0xFFADC6FF),
        surfaceTint = Color(0xFF445E91),
        outlineVariant = Color(0xFFC4C6D0),
        scrim = Color(0xFF000000),
    ),
    darkScheme = darkColorScheme(
        primary = Color(0xFFADC6FF),
        onPrimary = Color(0xFF102F60),
        primaryContainer = Color(0xFF2B4678),
        onPrimaryContainer = Color(0xFFD8E2FF),
        secondary = Color(0xFFBFC6DC),
        onSecondary = Color(0xFF293041),
        secondaryContainer = Color(0xFF3F4759),
        onSecondaryContainer = Color(0xFFDBE2F9),
        tertiary = Color(0xFFDEBCDF),
        onTertiary = Color(0xFF402843),
        tertiaryContainer = Color(0xFF583E5B),
        onTertiaryContainer = Color(0xFFFBD7FC),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF111318),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF44474F),
        onSurfaceVariant = Color(0xFFC4C6D0),
        outline = Color(0xFF8E9099),
        inverseOnSurface = Color(0xFF2F3036),
        inverseSurface = Color(0xFFE2E2E9),
        inversePrimary = Color(0xFF445E91),
        surfaceTint = Color(0xFFD8E2FF),
        outlineVariant = Color(0xFF44474F),
        scrim = Color(0xFF000000)
    ),
    lightTokenColors = JsonArray(),
    darkTokenColors = JsonArray(),
    lightTerminalColors = Properties().also {
        it["foreground"] = Color(0xFF1A1B20).toHex()
        it["background"] = Color(0xFFF9F9FF).toHex()
        it["cursor"] = "#373b41"

        it["color0"] = "#1d1f21"
        it["color1"] = "#CC342B"
        it["color2"] = "#198844"
        it["color3"] = "#FBA922"
        it["color4"] = "#3971ED"
        it["color5"] = "#A36AC7"
        it["color6"] = "#3971ED"
        it["color7"] = "#c5c8c6"
        it["color8"] = "#969896"
        it["color9"] = "#CC342B"
        it["color10"] = "#198844"
        it["color11"] = "#FBA922"
        it["color12"] = "#3971ED"
        it["color13"] = "#A36AC7"
        it["color14"] = "#3971ED"
        it["color15"] = "#ffffff"

        it["color16"] = "#F96A38"
        it["color17"] = "#3971ED"
        it["color18"] = "#282a2e"
        it["color19"] = "#373b41"
        it["color20"] = "#b4b7b4"
        it["color21"] = "#e0e0e0"
    },
    darkTerminalColors = Properties().also {
        it["background"] = Color(0xFF111318).toHex()
        it["foreground"] = Color(0xFFE2E2E9).toHex()
        it["cursor"] = "#6e6a86"

        // black
        it["color0"] = "#393552"
        it["color8"] = "#6e6a86"

        // red
        it["color1"] = "#eb6f92"
        it["color9"] = "#eb6f92"

        // green
        it["color2"] = "#3e8fb0"
        it["color10"] = "#3e8fb0"

        // yellow
        it["color3"] = "#f6c177"
        it["color11"] = "#f6c177"

        // blue
        it["color4"] = "#9ccfd8"
        it["color12"] = "#9ccfd8"

        // magenta
        it["color5"] = "#c4a7e7"
        it["color13"] = "#c4a7e7"

        // cyan
        it["color6"] = "#ea9a97"
        it["color14"] = "#ea9a97"

        // white
        it["color7"] = "#e0def4"
        it["color15"] = "#e0def4"
    },
)

val lime = ThemeHolder(
    id = "lime",
    name = "Lime",
    lightScheme = lightColorScheme(
        primary = Color(0xFF4C662B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCDEDA3),
        onPrimaryContainer = Color(0xFF354E16),
        secondary = Color(0xFF586249),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDCE7C8),
        onSecondaryContainer = Color(0xFF404A33),
        tertiary = Color(0xFF386663),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFBCECE7),
        onTertiaryContainer = Color(0xFF1F4E4B),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFF9FAEF),
        onBackground = Color(0xFF1A1C16),
        surface = Color(0xFFF9FAEF),
        onSurface = Color(0xFF1A1C16),
        surfaceVariant = Color(0xFFE1E4D5),
        onSurfaceVariant = Color(0xFF44483D),
        outline = Color(0xFF75796C),
        outlineVariant = Color(0xFFC5C8BA),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF2F312A),
        inverseOnSurface = Color(0xFFF1F2E6),
        inversePrimary = Color(0xFFB1D18A),
        surfaceTint = Color(0xFF4C662B),
        surfaceDim = Color(0xFFDADBD0),
        surfaceBright = Color(0xFFF9FAEF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF3F4E9),
        surfaceContainer = Color(0xFFEEEFE3),
        surfaceContainerHigh = Color(0xFFE8E9DE),
        surfaceContainerHighest = Color(0xFFE2E3D8)
    ),
    darkScheme = darkColorScheme(
        primary = Color(0xFFB1D18A),
        onPrimary = Color(0xFF1F3701),
        primaryContainer = Color(0xFF354E16),
        onPrimaryContainer = Color(0xFFCDEDA3),
        secondary = Color(0xFFBFCBAD),
        onSecondary = Color(0xFF2A331E),
        secondaryContainer = Color(0xFF404A33),
        onSecondaryContainer = Color(0xFFDCE7C8),
        tertiary = Color(0xFFA0D0CB),
        onTertiary = Color(0xFF003735),
        tertiaryContainer = Color(0xFF1F4E4B),
        onTertiaryContainer = Color(0xFFBCECE7),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF12140E),
        onBackground = Color(0xFFE2E3D8),
        surface = Color(0xFF12140E),
        onSurface = Color(0xFFE2E3D8),
        surfaceVariant = Color(0xFF44483D),
        onSurfaceVariant = Color(0xFFC5C8BA),
        outline = Color(0xFF8F9285),
        outlineVariant = Color(0xFF44483D),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFE2E3D8),
        inverseOnSurface = Color(0xFF2F312A),
        inversePrimary = Color(0xFF4C662B),
        surfaceTint = Color(0xFFB1D18A),
        surfaceDim = Color(0xFF12140E),
        surfaceBright = Color(0xFF383A32),
        surfaceContainerLowest = Color(0xFF0C0F09),
        surfaceContainerLow = Color(0xFF1A1C16),
        surfaceContainer = Color(0xFF1E201A),
        surfaceContainerHigh = Color(0xFF282B24),
        surfaceContainerHighest = Color(0xFF33362E)
    ),
    lightTokenColors = JsonArray(),
    darkTokenColors = JsonArray(),
    lightTerminalColors = Properties().also {
        it["foreground"] = Color(0xFF1A1C16).toHex()
        it["background"] = Color(0xFFF9FAEF).toHex()
        it["cursor"] = "#373b41"

        it["color0"] = "#1d1f21"
        it["color1"] = "#CC342B"
        it["color2"] = "#198844"
        it["color3"] = "#FBA922"
        it["color4"] = "#3971ED"
        it["color5"] = "#A36AC7"
        it["color6"] = "#3971ED"
        it["color7"] = "#c5c8c6"
        it["color8"] = "#969896"
        it["color9"] = "#CC342B"
        it["color10"] = "#198844"
        it["color11"] = "#FBA922"
        it["color12"] = "#3971ED"
        it["color13"] = "#A36AC7"
        it["color14"] = "#3971ED"
        it["color15"] = "#ffffff"

        it["color16"] = "#F96A38"
        it["color17"] = "#3971ED"
        it["color18"] = "#282a2e"
        it["color19"] = "#373b41"
        it["color20"] = "#b4b7b4"
        it["color21"] = "#e0e0e0"
    },
    darkTerminalColors = Properties().also {
        it["background"] = Color(0xFF12140E).toHex()
        it["foreground"] = Color(0xFFE2E3D8).toHex()
        it["cursor"] = "#6e6a86"

        // black
        it["color0"] = "#393552"
        it["color8"] = "#6e6a86"

        // red
        it["color1"] = "#eb6f92"
        it["color9"] = "#eb6f92"

        // green
        it["color2"] = "#3e8fb0"
        it["color10"] = "#3e8fb0"

        // yellow
        it["color3"] = "#f6c177"
        it["color11"] = "#f6c177"

        // blue
        it["color4"] = "#9ccfd8"
        it["color12"] = "#9ccfd8"

        // magenta
        it["color5"] = "#c4a7e7"
        it["color13"] = "#c4a7e7"

        // cyan
        it["color6"] = "#ea9a97"
        it["color14"] = "#ea9a97"

        // white
        it["color7"] = "#e0def4"
        it["color15"] = "#e0def4"
    },
)

val inbuiltThemes = listOf<ThemeHolder>(blueberry,lime)