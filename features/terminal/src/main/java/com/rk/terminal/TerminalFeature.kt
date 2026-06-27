package com.rk.terminal

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.Modifier
import com.rk.activities.terminal.Terminal
import com.rk.commands.CommandProvider
import com.rk.commands.global.TerminalCommand
import com.rk.filetree.FileAction
import com.rk.filetree.FileActionContext
import com.rk.filetree.FileActionType
import com.rk.filetree.FileActionProvider
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.app.InbuiltFeatures
import com.rk.feature.Feature
import com.rk.feature.SettingsRegistry
import com.rk.feature.SettingsCategory
import com.rk.feature.SettingsRoute
import com.rk.activities.settings.SettingsRoutes
import com.rk.commands.GlobalCommand
import com.rk.commands.ToolbarConfiguration
import com.rk.components.GlobalToolbarActions
import com.rk.settings.terminal.SettingsTerminalScreen
import com.rk.settings.terminal.TerminalCheckScreen
import com.rk.settings.terminal.TerminalExtraKeys
import com.rk.settings.editor.TerminalFontScreen

class TerminalFeature : Feature {
    override fun init(application: Application) {
        // Register the file action
        FileActionProvider.registerAction(TerminalAction)

        // Register settings categories
        SettingsRegistry.registerCategory(
            SettingsCategory(
                labelRes = strings.terminal,
                descriptionRes = strings.terminal_desc,
                iconRes = drawables.terminal,
                route = SettingsRoutes.TerminalSettings.route
            )
        )
        SettingsRegistry.registerCategory(
            SettingsCategory(
                labelRes = strings.runners,
                descriptionRes = strings.runners_desc,
                iconRes = drawables.run,
                route = SettingsRoutes.Runners.route
            )
        )

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
        SettingsRegistry.registerRoute(
            SettingsRoute(SettingsRoutes.Runners.route) { navController ->
                com.rk.settings.runners.RunnerSettings(navController = navController)
            }
        )
        SettingsRegistry.registerRoute(
            SettingsRoute(SettingsRoutes.HtmlRunner.route) {
                com.rk.settings.runners.HtmlRunnerSettings()
            }
        )

        // Register global command
        val command = TerminalCommand()
        CommandProvider.registerCommand(command)

        //assuming there's atleast one item already there
        ToolbarConfiguration.addGlobalToolbarCommand(command, index = 1)
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
        return file is FileWrapper && InbuiltFeatures.terminal.state.value
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}
