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

class SearchCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.search"

    override fun getLabel(): String = strings.search.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editorTab.editorState.isSearching = true
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.search)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F, ctrl = true)
}
