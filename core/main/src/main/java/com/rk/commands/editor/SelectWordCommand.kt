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

class SelectWordCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.select_word"

    override fun getLabel(): String = strings.select_word.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editor.selectCurrentWord()
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.select)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_W, ctrl = true)
}
