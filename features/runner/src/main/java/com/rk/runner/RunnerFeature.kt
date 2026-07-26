package com.rk.runner

import android.app.Application
import com.rk.activities.settings.SettingsRoutes
import com.rk.commands.CommandProvider
import com.rk.commands.ToolbarConfiguration
import com.rk.commands.editor.RunCommand
import com.rk.components.DialogRegistry
import com.rk.extension.api.DynamicRoute
import com.rk.feature.Feature
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.SettingsCategory
import com.rk.settings.SettingsRegistry
import com.rk.settings.runners.HtmlRunnerSettings
import com.rk.settings.runners.RunnerSettings

class RunnerFeature : Feature {
    override fun init(application: Application) {
        // Register RunnerSheet overlay
        DialogRegistry.register {
            if (RunnerUI.showRunnerDialog) {
                RunnerSheet()
            }
        }
        // Register settings category
        SettingsRegistry.registerCategory(
            SettingsCategory(
                labelRes = strings.runners,
                descriptionRes = strings.runners_desc,
                iconRes = drawables.run,
                route = SettingsRoutes.Runners.route,
            )
        )

        // Register settings routes
        SettingsRegistry.registerRoute(
            DynamicRoute(SettingsRoutes.Runners.route) { navController, _ ->
                RunnerSettings(navController = navController)
            }
        )
        SettingsRegistry.registerRoute(
            DynamicRoute(SettingsRoutes.HtmlRunner.route) { _, _ ->
                HtmlRunnerSettings()
            }
        )

        // Register Run command
        CommandProvider.registerCommand(RunCommand)
        ToolbarConfiguration.addGlobalToolbarCommand(RunCommand, 0)
    }
}
