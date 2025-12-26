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

class RedoCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.redo"

    override fun getLabel(): String = strings.redo.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val editor = editorActionContext.editor
        if (editor.canRedo()) editor.redo()
        editorActionContext.editorTab.editorState.updateUndoRedo()
    }

    override fun isEnabled(editorNonActionContext: EditorNonActionContext): Boolean {
        val editorState = editorNonActionContext.editorTab.editorState
        return editorState.editable && editorState.canRedo
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.redo)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_Y, ctrl = true)
}
