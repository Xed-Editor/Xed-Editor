package com.rk.commands.editor

import android.view.KeyEvent
import com.rk.commands.CommandContext
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.EditorNonActionContext
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class UndoCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.undo"

    override fun getLabel(): String = strings.undo.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val editor = editorActionContext.editor
        if (editor.canUndo()) editor.undo()
        editorActionContext.editorTab.editorState.updateUndoRedo()
    }

    override fun isEnabled(editorNonActionContext: EditorNonActionContext): Boolean {
        val editorState = editorNonActionContext.editorTab.editorState
        return editorState.editable && editorState.canUndo
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.undo)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_Z, ctrl = true)
}
