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

class UndoCommand : EditorCommand() {
    override val id: String = "editor.undo"

    override val repeatOnHold: Boolean = true

    override fun getLabel(): String = strings.undo.getString()

    override fun action(context: EditorActionContext) {
        val editor = context.editor
        if (editor.canUndo()) editor.undo()
        context.editorTab.editorState.updateUndoRedo()
    }

    override fun isEnabled(context: EditorNonActionContext): Boolean {
        val editorState = context.editorTab.editorState
        return editorState.editable && editorState.canUndo
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.undo)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_Z, ctrl = true)
}
