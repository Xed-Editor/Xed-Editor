package com.rk.runner

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.rk.activities.settings.SettingsRoutes
import com.rk.commands.CommandProvider
import com.rk.commands.ToolbarConfiguration
import com.rk.commands.editor.RunCommand
import com.rk.components.DialogProvider
import com.rk.components.DialogRegistry
import com.rk.extension.api.DynamicRoute
import com.rk.feature.Feature
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.SettingsCategory
import com.rk.settings.SettingsRegistry
import com.rk.settings.runners.HtmlRunnerSettings
import com.rk.settings.runners.RunnerSettings

class RunnerFeature : Feature {
    private var dialogProvider: DialogProvider? = null
    private var settingsCategory: SettingsCategory? = null
    private var runnersRoute: DynamicRoute? = null
    private var htmlRunnersRoute: DynamicRoute? = null

    override fun init(application: Application) {
        // Register RunnerSheet overlay
        dialogProvider =
            DialogProvider {
                val showRunnerDialog by RunnerUI.showRunnerDialog.collectAsState()
                if (showRunnerDialog) {
                    RunnerSheet()
                }
            }
                .also { DialogRegistry.register(it) }

        // Register settings category
        settingsCategory =
            SettingsCategory(
                    label = strings.runners.getString(),
                    description = strings.runners_desc.getString(),
                    icon = Icon.ResourceIcon(drawables.run),
                    route = SettingsRoutes.Runners.route,
                )
                .also { SettingsRegistry.registerCategory(it) }

        // Register settings routes
        runnersRoute =
            DynamicRoute(SettingsRoutes.Runners.route) { navController, _ ->
                    RunnerSettings(navController = navController)
                }
                .also { SettingsRegistry.registerRoute(it) }

        htmlRunnersRoute =
            DynamicRoute(SettingsRoutes.HtmlRunner.route) { _, _ -> HtmlRunnerSettings() }
                .also {
                    SettingsRegistry.registerRoute(it)
                }

        // Register Run command
        CommandProvider.registerCommand(RunCommand)
        ToolbarConfiguration.addGlobalToolbarCommand(RunCommand, 0)
    }

    override fun dispose(application: Application) {
        dialogProvider?.let { DialogRegistry.unregister(it) }
        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        runnersRoute?.let { SettingsRegistry.unregisterRoute(it) }
        htmlRunnersRoute?.let { SettingsRegistry.unregisterRoute(it) }

        CommandProvider.unregisterCommand(RunCommand)
        ToolbarConfiguration.removeGlobalToolbarCommand(RunCommand)
    }
}
