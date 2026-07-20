package com.rk.commands.editor

import android.view.KeyEvent
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.EditorNonActionContext
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class RedoCommand : EditorCommand() {
    override val id: String = "editor.redo"

    override fun getLabel(): String = strings.redo.getString()

    override fun action(context: EditorActionContext) {
        val editor = context.editor
        if (editor.canRedo()) editor.redo()
        context.editorTab.editorState.updateUndoRedo()
    }

    override fun isEnabled(context: EditorNonActionContext): Boolean {
        val editorState = context.editorTab.editorState
        return editorState.editable && editorState.canRedo
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.redo)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_Y, ctrl = true)
}
