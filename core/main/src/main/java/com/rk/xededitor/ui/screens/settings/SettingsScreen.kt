package com.rk.xededitor.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.category.PreferenceCategory
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.components.NextScreenCard
import com.rk.xededitor.ui.screens.settings.feature_toggles.Features

@Composable
fun SettingsScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.settings), backArrowVisible = true) {
        Categories(navController)
    }
}

@Composable
private fun Categories(navController: NavController) {

    PreferenceCategory(
        label = stringResource(id = strings.app),
        description = stringResource(id = strings.app_desc),
        iconResource = drawables.android,
        onNavigate = { navController.navigate(SettingsRoutes.AppSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(id = strings.editor),
        description = stringResource(id = strings.editor_desc),
        iconResource = drawables.edit,
        onNavigate = { navController.navigate(SettingsRoutes.EditorSettings.route) },
    )

    if (Features.terminal.value){
        PreferenceCategory(
            label = stringResource(id = strings.terminal),
            description = stringResource(id = strings.terminal_desc),
            iconResource = drawables.terminal,
            onNavigate = { navController.navigate(SettingsRoutes.TerminalSettings.route) },
        )
    }

    if (Features.git.value){
        PreferenceCategory(
            label = stringResource(id = strings.git),
            description = stringResource(id = strings.git_desc),
            iconResource = drawables.github,
            onNavigate = { navController.navigate(SettingsRoutes.GitSettings.route) },
        )
    }

    PreferenceCategory(
        label = stringResource(id = strings.feature_toggles),
        description = stringResource(id = strings.feature_toggles_desc),
        iconResource = drawables.settings,
        onNavigate = { navController.navigate(SettingsRoutes.FeatureToggles.route) },
    )

    if (Features.extensions.value){
        PreferenceCategory(
            label = stringResource(strings.ext),
            description = stringResource(strings.ext_desc),
            iconResource = drawables.extension,
            onNavigate = { navController.navigate(SettingsRoutes.Extensions.route) },
        )
    }

    if (Features.developerOptions.value){
        PreferenceCategory(
            label = "Developer Options",
            description = "Debugging options for ${strings.app_name.getString()}",
            iconResource = drawables.settings,
            onNavigate = { navController.navigate(SettingsRoutes.DeveloperOptions.route) },
        )
    }

    PreferenceCategory(
        label = stringResource(id = strings.about),
        description = stringResource(id = strings.about_desc),
        iconResource = drawables.android,
        onNavigate = { navController.navigate(SettingsRoutes.About.route) },
    )



}
