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

class CommandPaletteCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.command_palette"

    override fun getLabel(): String = strings.command_palette.getString()

    override fun action(actionContext: ActionContext) {
        commandContext.mainViewModel.showCommandPalette = true
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.command_palette)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_P, ctrl = true, shift = true)
}
