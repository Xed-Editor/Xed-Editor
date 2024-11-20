package com.rk.xededitor.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.rk.resources.strings
import com.rk.resources.drawable
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory

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
        iconResource = drawable.android,
        onNavigate = { navController.navigate(SettingsRoutes.AppSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(id = strings.editor),
        description = stringResource(id = strings.editor_desc),
        iconResource = drawable.edit,
        onNavigate = { navController.navigate(SettingsRoutes.EditorSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(id = strings.terminal),
        description = stringResource(id = strings.terminal_desc),
        iconResource = drawable.terminal,
        onNavigate = { navController.navigate(SettingsRoutes.TerminalSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(id = strings.git),
        description = stringResource(id = strings.git_desc),
        iconResource = drawable.git,
        onNavigate = { navController.navigate(SettingsRoutes.GitSettings.route) },
    )
    PreferenceCategory(
        label = stringResource(id = strings.about),
        description = stringResource(id = strings.about_desc),
        iconResource = drawable.android,
        onNavigate = { navController.navigate(SettingsRoutes.About.route) },
    )
}
