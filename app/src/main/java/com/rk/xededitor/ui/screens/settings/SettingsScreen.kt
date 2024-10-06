package com.rk.xededitor.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight

import com.rk.xededitor.R
import com.rk.xededitor.ui.theme.KarbonTheme

import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory

/*
 * The First Settings Screen.
 */
@Composable
fun SettingsScreen() {
    PreferenceLayout(
        label = stringResource(id = R.string.settings),
        backArrowVisible = true,
    ) {
        Categories()
    }
}

@Composable
private fun Categories() {
    PreferenceCategory(
        label = stringResource(id = R.string.app),
        description = stringResource(id = R.string.app_desc),
        iconResource = R.drawable.android,
        onNavigate = {
            /* TO-DO: go to app settings screen */
        }
    )

    PreferenceCategory(
        label = stringResource(id = R.string.editor),
        description = stringResource(id = R.string.editor_desc),
        iconResource = R.drawable.edit,
        onNavigate = {
            /* TO-DO: go to editor settings screen */
        }
    )

    PreferenceCategory(
        label = stringResource(id = R.string.plugin),
        description = stringResource(id = R.string.plugin_desc),
        iconResource = R.drawable.extension,
        onNavigate = {
            /* TO-DO: go to plugin settings screen */
        }
    )

    PreferenceCategory(
        label = stringResource(id = R.string.terminal),
        description = stringResource(id = R.string.terminal_desc),
        iconResource = R.drawable.terminal,
        onNavigate = {
            /* TO-DO: go to terminal settings screen */
        }
    )

    PreferenceCategory(
        label = stringResource(id = R.string.git),
        description = stringResource(id = R.string.git_desc),
        iconResource = R.drawable.git,
        onNavigate = {
            /* TO-DO: go to git settings screen */
        }
    )
}