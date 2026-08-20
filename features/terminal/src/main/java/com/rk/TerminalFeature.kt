package com.rk

import android.app.Application
import android.content.Intent
import com.rk.activities.main.MainActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.activities.terminal.Terminal
import com.rk.commands.CommandProvider
import com.rk.commands.ToolbarConfiguration
import com.rk.commands.global.TerminalCommand
import com.rk.drawer.AddProjectCategory
import com.rk.drawer.AddProjectOption
import com.rk.drawer.AddProjectRegistry
import com.rk.exec.pendingCommand
import com.rk.exec.ubuntuProcess
import com.rk.extension.api.DynamicRoute
import com.rk.feature.Feature
import com.rk.feature.FeatureRegistry
import com.rk.feature.FeatureToggle
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
import com.rk.lsp.servers.Emmet
import com.rk.lsp.servers.HTML
import com.rk.lsp.servers.TypeScript
import com.rk.lsp.servers.XML
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.RunnerManager
import com.rk.runner.runners.UniversalRunner
import com.rk.settings.Settings
import com.rk.settings.SettingsCategory
import com.rk.settings.SettingsRegistry
import com.rk.settings.editor.TerminalFontScreen
import com.rk.settings.terminal.SettingsTerminalScreen
import com.rk.settings.terminal.TerminalCheckScreen
import com.rk.settings.terminal.TerminalExtraKeys
import com.rk.utils.dialogRes
import com.rk.utils.toast

class TerminalFeature : Feature {
    override val toggle =
        FeatureToggle(
            name = strings.terminal_feature.getString(),
            key = "feature_terminal",
            default = true,
            icon = Icon.ResourceIcon(drawables.terminal),
        )

    private var settingsCategory: SettingsCategory? = null
    private var addProjectOption: AddProjectOption? = null
    private val routes = mutableListOf<DynamicRoute>()

    override fun init(application: Application) {

        // Register the file action
        FileActionProvider.registerAction(TerminalAction)

        // Register settings categories
        settingsCategory =
            SettingsCategory(
                    label = strings.terminal.getString(),
                    description = strings.terminal_desc.getString(),
                    icon = Icon.ResourceIcon(drawables.terminal),
                    route = SettingsRoutes.TerminalSettings.route,
                )
                .also { SettingsRegistry.registerCategory(it) }

        if (FeatureRegistry.isEnabled("feature_terminal")) {
            addProjectOption =
                AddProjectOption(
                        icon = Icon.ResourceIcon(drawables.terminal),
                        title = strings.terminal_home.getString(),
                        description = strings.terminal_home_desc.getString(),
                        category = AddProjectCategory.STORAGE,
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
                                MainActivity.instance
                                    ?.drawerViewModel
                                    ?.addFileTreeTab(FileWrapper(sandboxHomeDir()), true)
                            }
                            onDismiss()
                        },
                    )
                    .also { AddProjectRegistry.register(it) }
        }

        // Register settings routes
        routes.add(DynamicRoute(SettingsRoutes.TerminalSettings.route) { _, _ -> SettingsTerminalScreen() })
        routes.add(DynamicRoute(SettingsRoutes.TerminalExtraKeys.route) { _, _ -> TerminalExtraKeys() })
        routes.add(DynamicRoute(SettingsRoutes.TerminalCheck.route) { _, _ -> TerminalCheckScreen() })
        routes.add(DynamicRoute(SettingsRoutes.TerminalFontScreen.route) { _, _ -> TerminalFontScreen() })

        routes.forEach { SettingsRegistry.registerRoute(it) }

        // Register UniversalRunner dynamically
        RunnerManager.addBuiltInRunner(UniversalRunner)

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
        CommandProvider.registerCommand(TerminalCommand)

        // Assuming there's at least one item already there
        ToolbarConfiguration.addGlobalToolbarCommand(TerminalCommand, index = 1)

        // Register built-in LSP servers
        LspRegistry.addBuiltInServers(HTML, Emmet, CSS, TypeScript, Bash, XML)
    }

    override fun dispose(application: Application) {
        FileActionProvider.unregisterAction(TerminalAction)
        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        addProjectOption?.let { AddProjectRegistry.unregister(it) }
        routes.forEach { SettingsRegistry.unregisterRoute(it) }
        routes.clear()

        RunnerManager.removeBuiltInRunner(UniversalRunner)
        TerminalLauncher.handler = null
        SandboxedProcessRegistry.provider = null
        CommandProvider.unregisterCommand(TerminalCommand)
        ToolbarConfiguration.removeGlobalToolbarCommand(TerminalCommand)
        LspRegistry.removeBuiltInServers(HTML, Emmet, CSS, TypeScript, Bash, XML)
    }
}

object TerminalAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.terminal)
    override val title = strings.open_in_terminal.getString()

    override suspend fun action(context: FileActionContext) {
        val file = context.file
        val ctx = context.context

        val intent = Intent(ctx, Terminal::class.java)
        intent.putExtra("cwd", file.getAbsolutePath())
        ctx.startActivity(intent)
    }

    override suspend fun isSupported(file: FileObject, root: FileObject?): Boolean {
        return file is FileWrapper && FeatureRegistry.isEnabled("feature_terminal")
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}
