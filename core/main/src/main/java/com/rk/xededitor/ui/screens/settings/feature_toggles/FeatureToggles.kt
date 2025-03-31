package com.rk.xededitor.ui.screens.settings.feature_toggles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.components.SettingsToggle

object Features{
    val extensions = mutableStateOf(Settings.feature_extensions)
    val terminal = mutableStateOf(Settings.feature_terminal)
    val mutators = mutableStateOf(Settings.feature_mutators)
    val git = mutableStateOf(Settings.feature_git)
    val developerOptions = mutableStateOf(Settings.developerOptions)
}

@Composable
fun FeatureToggles(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.feature_toggles)) {
        PreferenceGroup {
            SettingsToggle(
                label = stringResource(strings.terminal)+" + Runners",
                default = Features.terminal.value,
                sideEffect = {
                    Settings.feature_terminal = it
                    Features.terminal.value = it
                }
            )
            SettingsToggle(
                label = stringResource(strings.enable_ext),
                default = Features.extensions.value,
                sideEffect = {
                    Settings.feature_extensions = it
                    Features.extensions.value = it
                }
            )
            SettingsToggle(
                label = stringResource(strings.mutators),
                default = Features.mutators.value,
                sideEffect = {
                    Settings.feature_mutators = it
                    Features.mutators.value = it
                }
            )

            SettingsToggle(
                label = stringResource(strings.git),
                default = Features.git.value,
                sideEffect = {
                    Settings.feature_git = it
                    Features.git.value = it
                }
            )

            SettingsToggle(
                label = "Developer Options",
                default = Features.developerOptions.value,
                sideEffect = {
                    Settings.developerOptions = it
                    Features.developerOptions.value = it
                }
            )
        }
    }
}