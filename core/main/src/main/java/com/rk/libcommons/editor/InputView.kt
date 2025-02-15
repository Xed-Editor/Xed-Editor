package com.rk.libcommons.editor

import android.graphics.Color
import android.util.Pair
import android.view.KeyEvent
import android.view.View.OnClickListener
import androidx.appcompat.app.AppCompatDelegate
import com.rk.libcommons.isDarkMode
import com.rk.settings.Settings
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView

fun getInputView(editor: CodeEditor): SymbolInputView {
    val darkTheme: Boolean = when (Settings.default_night_mode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> isDarkMode(editor.context)
    }

    return SymbolInputView(editor.context).apply {
        textColor = if (darkTheme) {
            Color.WHITE
        } else {
            Color.BLACK
        }

        val keys = mutableListOf<Pair<String, OnClickListener>>().apply {
            add(Pair("->", onClick {
                editor.onKeyDown(
                    KeyEvent.KEYCODE_TAB, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)
                )
            }))

            add(Pair("⌘", onClick {
                EventBus.showControlPanel()
            }))

            add(Pair("←", onClick {
                editor.onKeyDown(
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
                )
            }))

            add(Pair("↑", onClick {
                editor.onKeyDown(
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
                )

            }))

            add(Pair("→", onClick {
                editor.onKeyDown(
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
                )

            }))

            add(Pair("↓", onClick {
                editor.onKeyDown(
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
                )

            }))

            add(Pair("⇇", onClick {
                editor.onKeyDown(
                    KeyEvent.KEYCODE_MOVE_HOME,
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME)
                )
            }))

            add(Pair("⇉", onClick {
                editor.onKeyDown(
                    KeyEvent.KEYCODE_MOVE_END,
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END)
                )
            }))
        }

        addSymbols(keys.toTypedArray())

        addSymbols(
            arrayOf("(", ")", "\"", "{", "}", "[", "]", ";"),
            arrayOf("(", ")", "\"", "{", "}", "[", "]", ";")
        )

        bindEditor(editor)
    }
}