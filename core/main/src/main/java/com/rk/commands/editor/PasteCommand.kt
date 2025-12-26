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

class PasteCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.paste"

    override fun getLabel(): String = strings.paste.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editor.pasteText()
    }

    override fun isEnabled(editorNonActionContext: EditorNonActionContext): Boolean {
        return editorNonActionContext.editorTab.editorState.editable
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.paste)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_V, ctrl = true)
}
