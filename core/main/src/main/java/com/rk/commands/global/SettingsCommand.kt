package com.rk.commands.global

import android.content.Intent
import android.view.KeyEvent
import com.rk.activities.settings.SettingsActivity
import com.rk.commands.ActionContext
import com.rk.commands.CommandContext
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class SettingsCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.settings"

    override fun getLabel(): String = strings.settings.getString()

    override fun action(actionContext: ActionContext) {
        val activity = actionContext.currentActivity
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.settings)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_COMMA, ctrl = true)
}
