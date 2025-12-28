package com.rk.commands.global

import android.view.KeyEvent
import com.rk.commands.ActionContext
import com.rk.commands.CommandContext
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.components.addDialog
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class NewFileCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.new_file"

    override fun getLabel(): String = strings.new_file.getString()

    override fun action(actionContext: ActionContext) {
        addDialog = true
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.add)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_N, ctrl = true)
}
