package com.rk.commands.global

import android.app.AlertDialog
import android.view.KeyEvent
import com.rk.commands.ActionContext
import com.rk.commands.CommandContext
import com.rk.commands.CommandProvider
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.commands.KeybindingsManager
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class KeyboardShortcutsCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.keyboard_shortcuts"

    override fun getLabel(): String = "Keyboard Shortcuts"

    override fun action(actionContext: ActionContext) {
        val shortcuts = buildShortcutsList()
        val items = shortcuts.map { "${it.first}\n${it.second}" }.toTypedArray()

        AlertDialog.Builder(actionContext.currentActivity)
            .setTitle("Keyboard Shortcuts")
            .setItems(items) { _, _ -> }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun buildShortcutsList(): List<Pair<String, String>> {
        val shortcuts = mutableListOf<Pair<String, String>>()

        for (command in CommandProvider.commandList) {
            val keyCombo = KeybindingsManager.getKeyCombinationForCommand(command.id)
            if (keyCombo != null) {
                val keyName = keyCombo.getDisplayName()
                shortcuts.add(command.getLabel() to keyName)
            }
        }

        return shortcuts.sortedBy { it.first }
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.settings)

    override val defaultKeybinds: KeyCombination? = null
}
