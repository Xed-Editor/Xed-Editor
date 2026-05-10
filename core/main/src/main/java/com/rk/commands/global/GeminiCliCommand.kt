package com.rk.commands.global

import com.rk.ai.GeminiBridge
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
import com.rk.tabs.editor.EditorTab

class GeminiCliCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.gemini_cli"

    override fun getLabel(): String = strings.gemini_cli.getString()

    override fun action(actionContext: ActionContext) {
        val currentEditorTab = commandContext.mainViewModel.currentTab as? EditorTab
        val projectDir = currentEditorTab?.projectRoot?.getAbsolutePath()
        val bridge = projectDir?.let { GeminiBridge.ensureStarted(commandContext.mainViewModel, it) }
        val args =
            buildList {
                    add(localBinDir().child("gemini-cli").absolutePath)
                    add("--skip-trust")
                    projectDir?.let {
                        add("--include-directories")
                        add(it)
                    }
                }
                .toTypedArray()

        launchTerminal(
            actionContext.currentActivity,
            TerminalCommand(
                exe = "/bin/bash",
                args = args,
                id = "gemini-cli",
                terminatePreviousSession = false,
                workingDir = projectDir,
                env =
                    bridge?.let {
                        arrayOf(
                            "GEMINI_CLI_IDE_SERVER_PORT=${it.port}",
                            "GEMINI_CLI_IDE_AUTH_TOKEN=${it.token}",
                            "GEMINI_CLI_IDE_PID=${android.os.Process.myPid()}",
                            "GEMINI_CLI_IDE_WORKSPACE_PATH=${it.workspacePath}",
                        )
                    } ?: arrayOf(),
            ),
        )
    }

    override fun isSupported(): Boolean = InbuiltFeatures.terminal.state.value

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.auto_fix)
}
