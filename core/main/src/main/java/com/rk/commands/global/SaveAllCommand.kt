package com.rk.commands.global

import android.view.KeyEvent
import com.rk.DefaultScope
import com.rk.commands.ActionContext
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SaveAllCommand : GlobalCommand() {
    override val id: String = "global.save_all"

    override fun getLabel(): String = strings.save_all.getString()

    override fun execute(context: ActionContext) {
        commandContext.mainViewModel.editorTabs.forEach {
            DefaultScope.launch(Dispatchers.IO) { it.save() }
        }
    }

    override fun isEnabled(): Boolean {
        return commandContext.mainViewModel.editorTabs.any { it.editorState.isDirty } || Settings.auto_save
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.save)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_S, ctrl = true, alt = true)
}
