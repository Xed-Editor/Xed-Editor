package com.rk.xededitor.ui.screens.settings.feature_toggles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.extension.Hooks
import com.rk.libcommons.isFdroid
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.xededitor.ui.components.SettingsToggle

data class Feature(
    val name: String,
    val key: String,
    val default: Boolean,
    val onChange: ((Boolean) -> Unit)? = null,
) {
    val state: MutableState<Boolean> by lazy { mutableStateOf(Preference.getBoolean(key, default)) }
    fun setEnable(enable: Boolean) {
        Preference.setBoolean(key, enable)
        state.value = enable
        onChange?.invoke(enable)
    }
}

object InbuiltFeatures {
    val extensions =
        Feature(name = strings.enable_ext.getString(), key = "enable_extension", default = false)
    val terminal = Feature(
        name = strings.terminal.getString() + " + Runners",
        key = "feature_terminal",
        default = true
    )
    val mutators =
        Feature(name = strings.mutators.getString(), key = "feature_mutators", default = true)
    val developerOptions =
        Feature(name = "Developer Options", key = "developerOptions", default = false)
}

@Composable
fun FeatureToggles(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.feature_toggles)) {
        PreferenceGroup {
            SettingsToggle(
                label = InbuiltFeatures.terminal.name,
                default = InbuiltFeatures.terminal.state.value,
                sideEffect = {
                    InbuiltFeatures.terminal.setEnable(it)
                }
            )
            if (isFdroid) {
                SettingsToggle(
                    label = InbuiltFeatures.extensions.name,
                    default = InbuiltFeatures.extensions.state.value,
                    sideEffect = {
                        InbuiltFeatures.extensions.setEnable(it)
                    }
                )
            }

            SettingsToggle(
                label = InbuiltFeatures.mutators.name,
                default = InbuiltFeatures.mutators.state.value,
                sideEffect = {
                    InbuiltFeatures.mutators.setEnable(it)
                }
            )

            if (isFdroid) {
                SettingsToggle(
                    label = InbuiltFeatures.developerOptions.name,
                    default = InbuiltFeatures.developerOptions.state.value,
                    sideEffect = {
                        InbuiltFeatures.developerOptions.setEnable(it)
                    }
                )
            }

            Hooks.Settings.features.values.forEach { feature ->
                SettingsToggle(
                    label = feature.name,
                    default = feature.state.value,
                    sideEffect = {
                        feature.setEnable(it)
                    }
                )
            }
        }
    }
}