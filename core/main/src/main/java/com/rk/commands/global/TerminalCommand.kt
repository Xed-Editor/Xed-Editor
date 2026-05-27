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
import com.rk.tabs.editor.EditorTab
import com.rk.utils.showTerminalNotice
import java.io.File

class TerminalCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.terminal"

    override fun getLabel(): String = strings.terminal.getString()

    override fun action(actionContext: ActionContext) {
        val activity = actionContext.currentActivity
        showTerminalNotice(activity) {
            val cwd = computeCwd()
            val intent = Intent(activity, Terminal::class.java)
            if (cwd != null) intent.putExtra("cwd", cwd)
            activity.startActivity(intent)
        }
    }

    private fun computeCwd(): String? {
        val tab = commandContext.mainViewModel.currentTab
        if (tab is EditorTab) {
            val projectRoot = tab.projectRoot
            if (projectRoot != null) {
                val path = projectRoot.getAbsolutePath()
                if (path.isNotBlank() && path.startsWith("/")) return path
            }
            val filePath = tab.file.getAbsolutePath()
            if (filePath.startsWith("/")) {
                val file = File(filePath)
                return if (file.isDirectory) filePath else file.parent
            }
        }
        return null
    }

    override fun isSupported(): Boolean = InbuiltFeatures.terminal.state.value

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.terminal)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_J, ctrl = true)
}
