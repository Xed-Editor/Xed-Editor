package com.rk.xededitor.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.rk.xededitor.R
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory

@Composable
fun SettingsScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = R.string.settings), backArrowVisible = true) {
        Categories(navController)
    }
}

@Composable
private fun Categories(navController: NavController) {
    PreferenceCategory(
        label = stringResource(id = R.string.app),
        description = stringResource(id = R.string.app_desc),
        iconResource = R.drawable.android,
        onNavigate = { navController.navigate(SettingsRoutes.AppSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(id = R.string.editor),
        description = stringResource(id = R.string.editor_desc),
        iconResource = R.drawable.edit,
        onNavigate = { navController.navigate(SettingsRoutes.EditorSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(id = R.string.terminal),
        description = stringResource(id = R.string.terminal_desc),
        iconResource = R.drawable.terminal,
        onNavigate = { navController.navigate(SettingsRoutes.TerminalSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(id = R.string.git),
        description = stringResource(id = R.string.git_desc),
        iconResource = R.drawable.git,
        onNavigate = { navController.navigate(SettingsRoutes.GitSettings.route) },
    )
    PreferenceCategory(
        label = stringResource(id = R.string.about),
        description = stringResource(id = R.string.about_desc),
        iconResource = R.drawable.android,
        onNavigate = { navController.navigate(SettingsRoutes.About.route) },
    )
}
