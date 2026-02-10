package com.rk.settings.keybinds

import android.view.KeyEvent
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

data object KeyUtils {
    fun getKeyDisplayName(keyCode: Int): String {
        when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.META_SHIFT_ON -> return strings.shift.getString()
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.META_CTRL_ON -> return strings.ctrl.getString()
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.META_ALT_ON -> return strings.alt.getString()
            KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_META_RIGHT,
            KeyEvent.META_META_ON -> return "Meta"

            KeyEvent.KEYCODE_DPAD_DOWN -> return "Arrow Down"
            KeyEvent.KEYCODE_DPAD_UP -> return "Arrow Up"
            KeyEvent.KEYCODE_DPAD_LEFT -> return "Arrow Left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> return "Arrow Right"
            KeyEvent.KEYCODE_DEL -> return "Backspace"
            KeyEvent.KEYCODE_FORWARD_DEL -> return "Delete"
        }

        val keyName = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        return keyName.lowercase().split("_").joinToString(" ") { it[0].uppercase() + it.substring(1) }
    }

    fun getShortDisplayName(keyCode: Int): String {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> return "↓"
            KeyEvent.KEYCODE_DPAD_UP -> return "↑"
            KeyEvent.KEYCODE_DPAD_LEFT -> return "←"
            KeyEvent.KEYCODE_DPAD_RIGHT -> return "→"
            KeyEvent.KEYCODE_DEL -> return "⌫"
            KeyEvent.KEYCODE_FORWARD_DEL -> return "⌦"
            KeyEvent.KEYCODE_TAB -> return "⇥"
            KeyEvent.KEYCODE_ENTER -> return "↩"
            KeyEvent.KEYCODE_SPACE -> return "␣"
            KeyEvent.KEYCODE_MINUS -> return "-"
            KeyEvent.KEYCODE_EQUALS -> return "="
            KeyEvent.KEYCODE_SLASH -> return "/"
            KeyEvent.KEYCODE_BACKSLASH -> return "\\"
            KeyEvent.KEYCODE_PERIOD -> return "."
            KeyEvent.KEYCODE_COMMA -> return ","
            KeyEvent.KEYCODE_SEMICOLON -> return ";"
            KeyEvent.KEYCODE_APOSTROPHE -> return "'"
            KeyEvent.KEYCODE_GRAVE -> return "`"
            KeyEvent.KEYCODE_LEFT_BRACKET -> return "["
            KeyEvent.KEYCODE_RIGHT_BRACKET -> return "]"
        }

        val keyName = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        return keyName.lowercase().split("_").joinToString("") { it[0].uppercase() + it.substring(1) }
    }

    fun getKeyCodeFromChar(char: Char): Int {
        val keyName =
            when (char) {
                ' ' -> "SPACE"
                '\n' -> "ENTER"
                '-' -> "MINUS"
                '=' -> "EQUALS"
                '/' -> "SLASH"
                '\\' -> "BACKSLASH"
                '.' -> "PERIOD"
                ',' -> "COMMA"
                ';' -> "SEMICOLON"
                '\'' -> "APOSTROPHE"
                '`' -> "GRAVE"
                '[' -> "LEFT_BRACKET"
                ']' -> "RIGHT_BRACKET"
                else -> char.uppercase()
            }

        return KeyEvent.keyCodeFromString("KEYCODE_${keyName}")
    }

    fun getKeyIcon(keyCode: Int): Icon {
        return Icon.DrawableRes(
            when (keyCode) {
                KeyEvent.KEYCODE_SHIFT_LEFT -> drawables.shift
                KeyEvent.KEYCODE_DPAD_DOWN -> drawables.kbd_arrow_down
                KeyEvent.KEYCODE_DPAD_UP -> drawables.kbd_arrow_up
                KeyEvent.KEYCODE_DPAD_LEFT -> drawables.kbd_arrow_left
                KeyEvent.KEYCODE_DPAD_RIGHT -> drawables.kbd_arrow_right
                KeyEvent.KEYCODE_DEL -> drawables.backspace
                KeyEvent.KEYCODE_FORWARD_DEL -> drawables.backspace_mirrored
                KeyEvent.KEYCODE_TAB -> drawables.kbd_tab
                else -> drawables.keyboard
            }
        )
    }

    fun isModifierKey(keyCode: Int): Boolean {
        return keyCode in
            listOf(
                KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.META_CTRL_ON,
                KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.META_SHIFT_ON,
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_ALT_RIGHT,
                KeyEvent.META_ALT_ON,
                KeyEvent.KEYCODE_META_LEFT,
                KeyEvent.KEYCODE_META_RIGHT,
                KeyEvent.META_META_ON,
            )
    }

    fun isModifierKey(event: KeyEvent): Boolean = isModifierKey(event.keyCode)

    fun isModifierKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean = isModifierKey(event.nativeKeyEvent)
}
