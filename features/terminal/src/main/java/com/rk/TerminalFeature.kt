package com.rk

import android.app.Application
import android.content.Intent
import com.rk.activities.main.MainActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.activities.terminal.Terminal
import com.rk.commands.CommandProvider
import com.rk.commands.ToolbarConfiguration
import com.rk.commands.global.TerminalCommand
import com.rk.drawer.AddProjectOption
import com.rk.drawer.AddProjectRegistry
import com.rk.exec.pendingCommand
import com.rk.exec.ubuntuProcess
import com.rk.feature.Feature
import com.rk.feature.FeatureRegistry
import com.rk.feature.FeatureToggle
import com.rk.feature.SettingsCategory
import com.rk.feature.SettingsRegistry
import com.rk.feature.SettingsRoute
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.sandboxHomeDir
import com.rk.filetree.FileAction
import com.rk.filetree.FileActionContext
import com.rk.filetree.FileActionProvider
import com.rk.filetree.FileActionType
import com.rk.icons.Icon
import com.rk.lsp.LspRegistry
import com.rk.lsp.servers.Bash
import com.rk.lsp.servers.CSS
import com.rk.lsp.servers.ESLint
import com.rk.lsp.servers.Emmet
import com.rk.lsp.servers.HTML
import com.rk.lsp.servers.Markdown
import com.rk.lsp.servers.TypeScript
import com.rk.lsp.servers.XML
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.RunnerManager
import com.rk.runner.runners.UniversalRunner
import com.rk.settings.Settings
import com.rk.settings.editor.TerminalFontScreen
import com.rk.settings.terminal.SettingsTerminalScreen
import com.rk.settings.terminal.TerminalCheckScreen
import com.rk.settings.terminal.TerminalExtraKeys
import com.rk.utils.dialogRes
import com.rk.utils.toast

class TerminalFeature : Feature {
    override val toggle =
        FeatureToggle(
            nameRes = strings.terminal_feature,
            key = "feature_terminal",
            default = true,
            iconRes = drawables.terminal,
        )

    override fun init(application: Application) {

        // Register the file action
        FileActionProvider.registerAction(TerminalAction)

        // Register settings categories
        SettingsRegistry.registerCategory(
            SettingsCategory(
                labelRes = strings.terminal,
                descriptionRes = strings.terminal_desc,
                iconRes = drawables.terminal,
                route = SettingsRoutes.TerminalSettings.route,
            )
        )

        if (FeatureRegistry.isEnabled("feature_terminal")) {
            AddProjectRegistry.options.add(
                AddProjectOption(
                    icon = Icon.ResourceIcon(drawables.terminal),
                    titleRes = strings.terminal_home,
                    descriptionRes = strings.terminal_home_desc,
                    onClick = { onDismiss ->
                        if (!Settings.has_shown_terminal_dir_warning) {
                            dialogRes(
                                title = strings.attention.getString(),
                                msg = strings.warning_private_dir.getString(),
                                onOk = {
                                    Settings.has_shown_terminal_dir_warning = true
                                    MainActivity.instance
                                        ?.drawerViewModel
                                        ?.addFileTreeTab(FileWrapper(sandboxHomeDir()), true)
                                },
                            )
                        } else {
                            MainActivity.instance?.drawerViewModel?.addFileTreeTab(FileWrapper(sandboxHomeDir()), true)
                        }
                        onDismiss()
                    },
                )
            )
        }

        // Register settings routes
        SettingsRegistry.registerRoute(
            SettingsRoute(SettingsRoutes.TerminalSettings.route) {
                SettingsTerminalScreen()
            }
        )
        SettingsRegistry.registerRoute(
            SettingsRoute(SettingsRoutes.TerminalExtraKeys.route) {
                TerminalExtraKeys()
            }
        )
        SettingsRegistry.registerRoute(
            SettingsRoute(SettingsRoutes.TerminalCheck.route) {
                TerminalCheckScreen()
            }
        )
        SettingsRegistry.registerRoute(
            SettingsRoute(SettingsRoutes.TerminalFontScreen.route) {
                TerminalFontScreen()
            }
        )
        // Register UniversalRunner dynamically
        RunnerManager.registerRunner(UniversalRunner)

        // Register TerminalLauncher handler
        TerminalLauncher.handler = { activity, sandbox, exe, args, id, terminatePreviousSession, workingDir, env ->
            pendingCommand =
                com.rk.exec.TerminalCommand(
                    sandbox = sandbox,
                    exe = exe,
                    args = args,
                    id = id,
                    terminatePreviousSession = terminatePreviousSession,
                    workingDir = workingDir,
                    env = env,
                )
            try {
                val intent = Intent(activity, Terminal::class.java)
                activity.startActivity(intent)
            } catch (_: Exception) {
                toast("Terminal feature is not available in this build")
            }
        }

        // Register SandboxedProcessRegistry provider
        SandboxedProcessRegistry.provider = { command, workingDir, excludeMounts ->
            ubuntuProcess(excludeMounts, workingDir = workingDir, command = command)
        }

        // Register global command
        val command = TerminalCommand()
        CommandProvider.registerCommand(command)

        // Assuming there's at least one item already there
        ToolbarConfiguration.addGlobalToolbarCommand(command, index = 1)

        // Register built-in LSP servers
        LspRegistry.registerServer(Bash)
        LspRegistry.registerServer(CSS)
        LspRegistry.registerServer(ESLint)
        LspRegistry.registerServer(Emmet)
        LspRegistry.registerServer(HTML)
        LspRegistry.registerServer(Markdown)
        LspRegistry.registerServer(TypeScript)
        LspRegistry.registerServer(XML)
    }
}

object TerminalAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.terminal)
    override val title = strings.open_in_terminal.getString()

    override fun action(context: FileActionContext) {
        val file = context.file
        val ctx = context.context

        val intent = Intent(ctx, Terminal::class.java)
        intent.putExtra("cwd", file.getAbsolutePath())
        ctx.startActivity(intent)
    }

    override fun isSupported(file: FileObject): Boolean {
        return file is FileWrapper && FeatureRegistry.isEnabled("feature_terminal")
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}
