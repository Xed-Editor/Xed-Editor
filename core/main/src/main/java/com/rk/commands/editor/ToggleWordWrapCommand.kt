package com.rk.commands.editor

import android.view.KeyEvent
import com.rk.commands.CommandContext
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class ToggleWordWrapCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.toggle_word_wrap"

    override fun getLabel(): String = strings.toggle_word_wrap.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val editor = editorActionContext.editor
        editor.setWordwrap(!editor.isWordwrap, true, true)
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.edit_note)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_Z, alt = true)
}
