package com.rk.libcommons.editor

import android.util.Pair
import android.view.KeyEvent
import android.view.View.OnClickListener
import com.rk.xededitor.ui.activities.main.showControlPanel
import io.github.rosemoe.sora.widget.CodeEditor


private typealias onClick = OnClickListener

fun getInputView(editor: CodeEditor,surfaceColor: Int,onSurfaceColor: Int): SymbolInputView {

    return SymbolInputView(editor.context).apply {
        textColor = onSurfaceColor
        setBgColor(surfaceColor)

        val keys = mutableListOf<Pair<String, OnClickListener>>().apply {

            add(Pair("->", onClick {
                editor.onKeyDown(
                    KeyEvent.KEYCODE_TAB, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)
                )
            }))

            add(Pair("⌘", onClick {
                showControlPanel = true
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