package com.rk.commands.global

import android.view.KeyEvent
import com.rk.commands.ActionContext
import com.rk.commands.CommandContext
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.components.fileSearchDialog
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class SearchFileFolderCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.search_file_folder"

    override fun getLabel(): String = strings.search_file_folder.getString()

    override fun action(actionContext: ActionContext) {
        fileSearchDialog = true
    }

    override fun isEnabled(): Boolean {
        return currentDrawerTab is FileTreeTab
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.search)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_P, ctrl = true)
}
