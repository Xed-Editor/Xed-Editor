package com.rk.commands.global

import android.view.KeyEvent
import com.rk.commands.ActionContext
import com.rk.commands.CommandContext
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.components.codeSearchDialog
import com.rk.filetree.currentDrawerTab
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class SearchCodeCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.search_code"

    override fun getLabel(): String = strings.search_code.getString()

    override fun action(actionContext: ActionContext) {
        codeSearchDialog = true
    }

    override fun isEnabled(): Boolean {
        return currentDrawerTab != null
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.search)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_F, ctrl = true, shift = true)
}
