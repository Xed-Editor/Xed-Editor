package com.rk.commands.global

import com.rk.ai.AiProvider
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
import com.rk.settings.Settings
import com.rk.settings.app.InbuiltFeatures
import com.rk.tabs.editor.EditorTab
import java.io.File

class AiCliCommand(commandContext: CommandContext) : GlobalCommand(commandContext) {
    override val id: String = "global.ai_cli"

    override fun getLabel(): String {
        val agent = AiProvider.sessionManager?.currentAgent
        return "${agent?.displayName ?: "AI"} CLI"
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
        val bridge = AiProvider.ideBridge?.ensureStarted(commandContext.mainViewModel, workspaceDir) ?: return
        val agent = AiProvider.sessionManager?.resolveAgent(Settings.ai_agent) ?: return
        val launcher = localBinDir().child(agent.shellScriptName).absolutePath
        val agentArgs = agent.buildArgs(emptyList(), workspaceDir)
        val args = buildList {
                add(launcher)
                addAll(agentArgs)
            }

        launchTerminal(
            actionContext.currentActivity,
            TerminalCommand(
                exe = "/bin/bash",
                args = args,
                id = "${agent.name}-cli",
                terminatePreviousSession = false,
                workingDir = workspaceDir,
                env = AiProvider.agentEnvBuilder?.buildMinimalBridgeEnv(bridge, workspaceDir) ?: emptyList(),
            ),
        )
    }

    override fun isSupported(): Boolean = InbuiltFeatures.terminal.state.value

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.auto_fix)
}
