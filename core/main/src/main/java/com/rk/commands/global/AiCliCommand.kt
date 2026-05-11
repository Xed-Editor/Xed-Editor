package com.rk.commands.global

import com.rk.ai.IdeBridge
import com.rk.ai.ideWorkspacePath
import com.rk.ai.session.AiSessionManager
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

class AiCliCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.ai_cli"

    override fun getLabel(): String {
        val agent = AiSessionManager.currentAgent
        return "${agent.displayName} CLI"
    }

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
        val bridge = IdeBridge.ensureStarted(commandContext.mainViewModel, workspaceDir) ?: return
        val agent = AiSessionManager.currentAgent
        val launcher = localBinDir().child(agent.shellScriptName).absolutePath
        val agentArgs = agent.buildArgs(emptyList(), workspaceDir)
        val args = buildList {
                add(launcher)
                addAll(agentArgs)
            }.toTypedArray()

        launchTerminal(
            actionContext.currentActivity,
            TerminalCommand(
                exe = "/bin/bash",
                args = args,
                id = "${agent.name}-cli",
                terminatePreviousSession = false,
                workingDir = workspaceDir,
                env = arrayOf(
                    "WKDIR=$workspaceDir",
                    "GEMINI_CLI_IDE_SERVER_PORT=${bridge.port}",
                    "GEMINI_CLI_IDE_AUTH_TOKEN=${bridge.token}",
                    "GEMINI_CLI_IDE_PID=${android.os.Process.myPid()}",
                    "GEMINI_CLI_IDE_WORKSPACE_PATH=${ideWorkspacePath(workspaceDir)}",
                    "IDE_SERVER_PORT=${bridge.port}",
                    "IDE_AUTH_TOKEN=${bridge.token}",
                    "IDE_WORKSPACE_PATH=${ideWorkspacePath(workspaceDir)}",
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
