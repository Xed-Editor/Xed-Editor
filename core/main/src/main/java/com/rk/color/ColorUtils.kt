package com.rk.color

import androidx.compose.ui.graphics.Color
import io.github.rosemoe.sora.lsp.utils.ColorUtils
import kotlin.math.abs
import kotlin.math.roundToInt

fun String.parseColor(): Color? {
    return ColorUtils.parseColor(this)?.let { Color(it) }
}

fun String.parseUnknownColor(): Pair<Color, ColorFormat>? {
    parseHsl()?.let {
        return it to ColorFormat.HSL
    }
    parseRgb()?.let {
        return it to ColorFormat.RGB
    }
    parseHex()?.let {
        return it to ColorFormat.HEX
    }
    return null
}

fun Color.toHex(): String {
    val a = (alpha * 255).roundToInt()
    val r = (red * 255).roundToInt()
    val g = (green * 255).roundToInt()
    val b = (blue * 255).roundToInt()

    return if (a == 255) {
        String.format("#%02X%02X%02X", r, g, b)
    } else {
        String.format("#%02X%02X%02X%02X", r, g, b, a)
    }
}

fun String.parseHex(): Color? {
    return ColorUtils.parseHex(this)?.let { Color(it) }
}

fun Color.toRgb(): String {
    val r = (red * 255).roundToInt()
    val g = (green * 255).roundToInt()
    val b = (blue * 255).roundToInt()

    return if (alpha == 1f) {
        "rgb($r, $g, $b)"
    } else {
        "rgba($r, $g, $b, $alpha)"
    }
}

fun String.parseRgb(): Color? {
    return ColorUtils.parseRgb(this)?.let { Color(it) }
}

private fun rgbToHsl(rf: Float, gf: Float, bf: Float): FloatArray {
    val max = maxOf(rf, gf, bf)
    val min = minOf(rf, gf, bf)
    val delta = max - min

    var h = 0f
    val l = (max + min) / 2f
    val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))

    if (delta != 0f) {
        h =
            when (max) {
                rf -> ((gf - bf) / delta) % 6f
                gf -> ((bf - rf) / delta) + 2f
                else -> ((rf - gf) / delta) + 4f
            }
        h /= 6f
        if (h < 0f) h += 1f
    }

    return floatArrayOf(h, s, l)
}

fun Color.toHsl(): String {
    val hsl = rgbToHsl(red, green, blue)
    val h = (hsl[0] * 360f).roundToInt()
    val s = (hsl[1] * 100f).roundToInt()
    val l = (hsl[2] * 100f).roundToInt()

    return if (alpha == 1f) {
        "hsl($h, $s%, $l%)"
    } else {
        "hsla($h, $s%, $l%, $alpha)"
    }
}

fun String.parseHsl(): Color? {
    return ColorUtils.parseHsl(this)?.let { Color(it) }
}
