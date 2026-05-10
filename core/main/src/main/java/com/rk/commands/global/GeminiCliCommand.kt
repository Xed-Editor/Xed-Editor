package com.rk.commands.global

import com.rk.commands.ActionContext
import com.rk.commands.CommandContext
import com.rk.commands.GlobalCommand
import com.rk.exec.TerminalCommand
import com.rk.exec.launchTerminal
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.app.InbuiltFeatures

class GeminiCliCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.gemini_cli"

    override fun getLabel(): String = strings.gemini_cli.getString()

    override fun action(actionContext: ActionContext) {
        launchTerminal(
            actionContext.currentActivity,
            TerminalCommand(
                exe = "/bin/bash",
                args = arrayOf(localBinDir().child("gemini-cli").absolutePath),
                id = "gemini-cli",
                terminatePreviousSession = false,
            ),
        )
    }

    override fun isSupported(): Boolean = InbuiltFeatures.terminal.state.value

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.auto_fix)
}
