package com.rk.commands.editor

import android.view.KeyEvent
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class JumpToLineCommand : EditorCommand() {
    override val id: String = "editor.jump_to_line"

    override fun getLabel(): String = strings.jump_to_line.getString()

    override fun action(context: EditorActionContext) {
        context.editorTab.editorState.apply {
            showJumpToLineDialog = true
            val line = context.editor.cursor.leftLine + 1
            val column = context.editor.cursor.leftColumn + 1
            jumpToLineValue = "$line:$column"
        }
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.arrow_outward)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_G, ctrl = true)
}
