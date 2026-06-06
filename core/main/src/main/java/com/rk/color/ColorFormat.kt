package com.rk.color

import androidx.compose.ui.graphics.Color

sealed class ColorFormat(val label: String) {
    object HEX : ColorFormat("Hex") {
        override fun getString(color: Color) = color.toHex()

        override fun getColor(string: String) = string.parseHex()
    }

    object RGB : ColorFormat("RGB") {
        override fun getString(color: Color) = color.toRgb()

        override fun getColor(string: String) = string.parseRgb()
    }

    object HSL : ColorFormat("HSL") {
        override fun getString(color: Color) = color.toHsl()

        override fun getColor(string: String) = string.parseHsl()
    }

    abstract fun getString(color: Color): String

    abstract fun getColor(string: String): Color?

    companion object {
        val availableTypes by lazy {
            listOf(
                HEX,
                RGB,
                HSL,
            )
        }
    }
}
