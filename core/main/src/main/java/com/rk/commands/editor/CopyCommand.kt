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

class CopyCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.copy"

    override fun getLabel(): String = strings.copy.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editor.copyText()
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.copy)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_C, ctrl = true)
}
