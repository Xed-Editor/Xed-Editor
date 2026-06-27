package com.rk.runner

import android.app.Application
import com.rk.commands.CommandProvider
import com.rk.commands.editor.RunCommand
import com.rk.feature.Feature
import com.rk.feature.SettingsRegistry
import com.rk.feature.SettingsCategory
import com.rk.feature.SettingsRoute
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.DialogRegistry
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.runners.RunnerSettings
import com.rk.settings.runners.HtmlRunnerSettings

class RunnerFeature : Feature {
    override fun init(application: Application) {
        // Register RunnerSheet overlay
        DialogRegistry.dialogs.add {
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
                route = SettingsRoutes.Runners.route
            )
        )

        // Register settings routes
        SettingsRegistry.registerRoute(
            SettingsRoute(SettingsRoutes.Runners.route) { navController ->
                RunnerSettings(navController = navController)
            }
        )
        SettingsRegistry.registerRoute(
            SettingsRoute(SettingsRoutes.HtmlRunner.route) {
                HtmlRunnerSettings()
            }
        )

        // Register Run command
        val runCommand = RunCommand()
        CommandProvider.registerCommand(runCommand)
    }
}
