package com.rk.commands.global

import android.view.KeyEvent
import com.rk.commands.ActionContext
import com.rk.commands.CommandContext
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.tabs.editor.EditorTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SaveAllCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.save_all"

    override fun getLabel(): String = strings.save_all.getString()

    override fun action(actionContext: ActionContext) {
        commandContext.mainViewModel.tabs.filterIsInstance<EditorTab>().forEach {
            GlobalScope.launch(Dispatchers.IO) { it.save() }
        }
    }

    override fun isEnabled(): Boolean {
        return commandContext.mainViewModel.tabs.isNotEmpty()
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.save)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_S, ctrl = true, shift = true)
}
