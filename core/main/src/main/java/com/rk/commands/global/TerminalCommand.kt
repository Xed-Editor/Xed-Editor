package com.rk.commands.global

import android.content.Intent
import android.view.KeyEvent
import com.rk.activities.terminal.Terminal
import com.rk.commands.ActionContext
import com.rk.commands.CommandContext
import com.rk.commands.GlobalCommand
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.app.InbuiltFeatures
import com.rk.utils.showTerminalNotice

class TerminalCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.terminal"

    override fun getLabel(): String = strings.terminal.getString()

    override fun action(actionContext: ActionContext) {
        val activity = actionContext.currentActivity
        showTerminalNotice(activity) {
            val intent =
                Intent(activity, Terminal::class.java).apply {
                    commandContext.mainViewModel.currentTab?.file?.let { currentFile ->
                        //                                //                                val currentFile =
                        // viewModel.currentTab?.file ?:
                        //                                // return@apply
                        //                                //                                val currentPath =
                        // currentFile.getAbsolutePath()
                        //                                //                                val project =
                        //                                //                                    tabs
                        //                                //                                        .filter {
                        //                                // currentPath.startsWith(it.fileObject.getAbsolutePath()) }
                        //                                //                                        .maxByOrNull {
                        //                                // it.fileObject.getAbsolutePath().length } ?: return@apply
                        //                                //                                putExtra("cwd",

                        // TODO: Fix this
                    }
                }
            activity.startActivity(intent)
        }
    }

    override fun isSupported(): Boolean = InbuiltFeatures.terminal.state.value

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.terminal)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_J, ctrl = true)
}
