package com.rk.commands.global

import com.rk.ai.GeminiBridge
import com.rk.ai.geminiIdeWorkspacePath
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
import java.io.File

class GeminiCliCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.gemini_cli"

    override fun getLabel(): String = strings.gemini_cli.getString()

    override fun action(actionContext: ActionContext) {
        val currentEditorTab = commandContext.mainViewModel.currentTab as? EditorTab
        val workspaceDir = currentEditorTab?.projectRoot?.getAbsolutePath()?.takeIf { it.isNotBlank() }
            ?: currentEditorTab?.file?.getAbsolutePath()
                ?.takeIf { it.isNotBlank() && it.startsWith("/") }
                ?.let { path ->
                    val file = File(path)
                    if (file.isDirectory) file.absolutePath else file.parent
                }
            ?: "/storage/emulated/0"
        val bridge = GeminiBridge.ensureStarted(commandContext.mainViewModel, workspaceDir)
        val args =
            buildList {
                    add(localBinDir().child("gemini-cli").absolutePath)
                    add("--skip-trust")
                    add("--include-directories")
                    add(workspaceDir)
                }
                .toTypedArray()

        launchTerminal(
            actionContext.currentActivity,
            TerminalCommand(
                exe = "/bin/bash",
                args = args,
                id = "gemini-cli",
                terminatePreviousSession = false,
                workingDir = workspaceDir,
                env =
                    arrayOf(
                        "GEMINI_CLI_IDE_SERVER_PORT=${bridge.port}",
                        "GEMINI_CLI_IDE_AUTH_TOKEN=${bridge.token}",
                        "GEMINI_CLI_IDE_PID=${android.os.Process.myPid()}",
                        "GEMINI_CLI_IDE_WORKSPACE_PATH=${geminiIdeWorkspacePath(bridge.workspacePath)}",
                        "TERM_PROGRAM=vscode",
                        "TERM_PROGRAM_VERSION=1.0.0",
                        "VSCODE_PID=${android.os.Process.myPid()}",
                        "EDITOR=vim",
                        "VISUAL=vim",
                    ),
            ),
        )
    }

    override fun isSupported(): Boolean = InbuiltFeatures.terminal.state.value

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.auto_fix)
}
