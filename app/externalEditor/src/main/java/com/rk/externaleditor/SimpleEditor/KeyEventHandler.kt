package com.rk.externaleditor.SimpleEditor

import android.view.KeyEvent
import com.rk.externaleditor.R
import io.github.rosemoe.sora.widget.CodeEditor

object KeyEventHandler {
    
    fun onKeyEvent(keyEvent: KeyEvent, editor: CodeEditor, activity: SimpleEditor) {
        if (keyEvent.isCtrlPressed) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_S -> {
                    HandleMenuItemClick.handle(activity, R.id.action_save)
                }
                
                KeyEvent.KEYCODE_PLUS,
                70 -> {
                    editor.let {
                        if (it.textSizePx < 57) {
                            it.textSizePx += 2
                        }
                    }
                }
                
                KeyEvent.KEYCODE_MINUS -> {
                    editor.let {
                        if (it.textSizePx > 8) {
                            it.textSizePx -= 2
                        }
                    }
                }
                
                KeyEvent.KEYCODE_F -> {
                    HandleMenuItemClick.handle(activity, R.id.search)
                }
                
                KeyEvent.KEYCODE_P -> {
                    HandleMenuItemClick.handle(activity, R.id.action_print)
                }
                
                KeyEvent.KEYCODE_G -> {
                    val line = 0
                    var column =
                        (editor.text.getLine(line).length).coerceAtMost(
                            editor.cursor.leftColumn
                        )
                    if (column < 0) {
                        column = 0
                    }
                    editor.cursor.set(line, column)
                }
                
                KeyEvent.KEYCODE_B -> {
                    val line = editor.text.lineCount - 1
                    var column =
                        (editor.text.getLine(line).length - 1).coerceAtMost(
                            editor.cursor.leftColumn
                        )
                    if (column < 0) {
                        column = 0
                    }
                    editor.cursor.set(line, column)
                }
            }
            
        }
    }
}
