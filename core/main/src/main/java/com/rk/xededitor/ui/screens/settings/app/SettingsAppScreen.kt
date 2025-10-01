package com.rk.xededitor.ui.screens.settings.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.App
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.settings.Preference
import com.rk.xededitor.ui.activities.settings.SettingsRoutes

data class Feature(
    val nameRes: Int,
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
        Feature(nameRes = strings.extensions, key = "enable_extension", default = false)
    val terminal =
        Feature(
            nameRes = strings.terminal_feature,
            key = "feature_terminal",
            default = true
        )
    val mutators =
        Feature(nameRes = strings.mutators, key = "feature_mutators", default = true)
    val developerOptions =
        Feature(nameRes = strings.debug_options, key = "developerOptions", default = false)
}

@Composable
fun SettingsAppScreen(activity: SettingsActivity,navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.app), backArrowVisible = true) {
       val context = LocalContext.current

        PreferenceGroup {
            SettingsToggle(
                label = stringResource(strings.lang),
                description = stringResource(strings.lang_desc),
                showSwitch = false,
                default = false,
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                sideEffect = {
                    navController.navigate(SettingsRoutes.LanguageScreen.route)
                }
            )

            SettingsToggle(
                label = stringResource(strings.check_for_updates),
                description = stringResource(strings.check_for_updates_desc),
                default = Settings.check_for_update,
                sideEffect = {
                    Settings.check_for_update = it
                })

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                SettingsToggle(
                    label = stringResource(strings.manage_storage),
                    description = stringResource(strings.manage_storage_desc),
                    isEnabled = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q,
                    showSwitch = false,
                    default = false,
                    endWidget = {
                        Icon(
                            modifier = Modifier.padding(16.dp),
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    sideEffect = {
                        val intent =
                            Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    }
                )
            }

        }

        PreferenceGroup(heading = stringResource(strings.feature_toggles)) {
            SettingsToggle(
                label = stringResource(InbuiltFeatures.terminal.nameRes),
                default = InbuiltFeatures.terminal.state.value,
                sideEffect = {
                    InbuiltFeatures.terminal.setEnable(it)
                }
            )
            if (App.isFDroid) {
                SettingsToggle(
                    label = stringResource(InbuiltFeatures.extensions.nameRes),
                    default = InbuiltFeatures.extensions.state.value,
                    sideEffect = {
                        InbuiltFeatures.extensions.setEnable(it)
                    }
                )
            }

            SettingsToggle(
                label = stringResource(InbuiltFeatures.mutators.nameRes),
                default = InbuiltFeatures.mutators.state.value,
                sideEffect = {
                    InbuiltFeatures.mutators.setEnable(it)
                }
            )

            if (App.isFDroid) {
                SettingsToggle(
                    label = stringResource(InbuiltFeatures.developerOptions.nameRes),
                    default = InbuiltFeatures.developerOptions.state.value,
                    sideEffect = {
                        InbuiltFeatures.developerOptions.setEnable(it)
                    }
                )
            }
        }
    }
}