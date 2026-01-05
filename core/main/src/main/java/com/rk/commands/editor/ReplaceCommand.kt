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

class ReplaceCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.replace"

    override fun getLabel(): String = strings.replace.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editorTab.editorState.apply {
            editorActionContext.editor.getSelectedText()?.let { searchKeyword = it }
            isSearching = true
            isReplaceShown = true
        }
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.find_replace)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_H, ctrl = true)
}
