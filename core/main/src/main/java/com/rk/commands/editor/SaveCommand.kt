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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SaveCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.save"

    override fun getLabel(): String = strings.save.getString()

    override fun action(editorActionContext: EditorActionContext) {
        GlobalScope.launch(Dispatchers.IO) { editorActionContext.editorTab.save() }
    }

    override fun isEnabled(editorNonActionContext: EditorNonActionContext): Boolean {
        return editorNonActionContext.editorTab.file.canWrite()
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.save)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_S, ctrl = true)
}
