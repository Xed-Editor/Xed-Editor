package com.rk.xededitor.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.screens.settings.app.showExtensions
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.category.PreferenceCategory
import com.rk.xededitor.ui.components.NextScreenCard

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

    PreferenceCategory(
        label = stringResource(id = strings.terminal),
        description = stringResource(id = strings.terminal_desc),
        iconResource = drawables.terminal,
        onNavigate = { navController.navigate(SettingsRoutes.TerminalSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(id = strings.git),
        description = stringResource(id = strings.git_desc),
        iconResource = drawables.github,
        onNavigate = { navController.navigate(SettingsRoutes.GitSettings.route) },
    )
    PreferenceCategory(
        label = stringResource(id = strings.about),
        description = stringResource(id = strings.about_desc),
        iconResource = drawables.android,
        onNavigate = { navController.navigate(SettingsRoutes.About.route) },
    )

    if (showExtensions.value){
        PreferenceCategory(
            label = stringResource(strings.ext),
            description = stringResource(strings.ext_desc),
            iconResource = drawables.extension,
            onNavigate = { navController.navigate(SettingsRoutes.Extensions.route) },
        )
    }


}
