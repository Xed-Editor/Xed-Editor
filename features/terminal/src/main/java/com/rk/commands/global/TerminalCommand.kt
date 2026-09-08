package com.rk.commands.global

import android.content.Intent
import android.view.KeyEvent
import com.rk.activities.terminal.Terminal
import com.rk.commands.ActionContext
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.feature.FeatureRegistry
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

object TerminalCommand : GlobalCommand() {
    override val id: String = "global.terminal"

    override fun getLabel(): String = strings.terminal.getString()

    override fun execute(context: ActionContext) {
        val activity = context.currentActivity
        val intent = Intent(activity, Terminal::class.java)
        activity.startActivity(intent)
    }

    override fun isSupported(): Boolean = FeatureRegistry.isEnabled("feature_terminal")

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.terminal)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_J, ctrl = true)
}
