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

class JumpToLineCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.jump_to_line"

    override fun getLabel(): String = strings.jump_to_line.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editorTab.editorState.showJumpToLineDialog = true
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.arrow_outward)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_G, ctrl = true)
}
