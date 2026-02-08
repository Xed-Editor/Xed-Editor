package com.rk.settings.app

import android.content.Intent
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.rk.activities.settings.SettingsActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.BasicToggle
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.file.toFileObject
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.ReactiveSettings
import com.rk.settings.Settings
import com.rk.settings.editor.refreshEditors
import com.rk.theme.amoled
import com.rk.theme.currentTheme
import com.rk.theme.dynamicTheme
import com.rk.utils.dialog
import com.rk.utils.toast
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Feature(
    val nameRes: Int,
    val key: String,
    val default: Boolean,
    val onChange: ((Boolean) -> Unit)? = null,
    val supported: Boolean = true,
) {
    val state: MutableState<Boolean> by lazy { mutableStateOf(supported && Preference.getBoolean(key, default)) }

    fun setEnable(enable: Boolean) {
        if (!supported) return

        Preference.setBoolean(key, enable)
        state.value = enable
        onChange?.invoke(enable)
    }
}

object InbuiltFeatures {
    val terminal = Feature(nameRes = strings.terminal_feature, key = "feature_terminal", default = true)
    val debugMode = Feature(nameRes = strings.debug_options, key = "debug_mode", default = BuildConfig.DEBUG)
    val extensions = Feature(nameRes = strings.ext, key = "enable_extension", default = true)
    val git =
        Feature(
            nameRes = strings.git,
            key = "enable_git",
            default = true,
            supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        )
}

@Composable
fun SettingsAppScreen(activity: SettingsActivity, navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.app), backArrowVisible = true) {
        val scope = rememberCoroutineScope()
        val gson = remember { GsonBuilder().setPrettyPrinting().create() }

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
                        contentDescription = null,
                    )
                },
                sideEffect = { navController.navigate(SettingsRoutes.LanguageScreen.route) },
            )

            SettingsToggle(
                label = stringResource(strings.check_for_updates),
                description = stringResource(strings.check_for_updates_desc),
                default = Settings.check_for_update,
                sideEffect = { Settings.check_for_update = it },
            )

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                SettingsToggle(
                    label = stringResource(strings.manage_storage),
                    description = stringResource(strings.manage_storage_desc),
                    showSwitch = false,
                    default = false,
                    endWidget = {
                        Icon(
                            modifier = Modifier.padding(16.dp),
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    sideEffect = {
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = "package:${activity.packageName}".toUri()
                        activity.startActivity(intent)
                    },
                )
            }
        }

        PreferenceGroup(heading = stringResource(strings.feature_toggles)) {
            val activity = LocalActivity.current

            BasicToggle(
                label = stringResource(InbuiltFeatures.debugMode.nameRes),
                checked = InbuiltFeatures.debugMode.state.value,
                onSwitch = {
                    if (it) {
                        dialog(
                            context = activity,
                            title = strings.attention.getString(),
                            msg = strings.debug_mode_warn.getString(),
                            onCancel = { InbuiltFeatures.debugMode.setEnable(false) },
                            onOk = { InbuiltFeatures.debugMode.setEnable(true) },
                        )
                    } else {
                        InbuiltFeatures.debugMode.setEnable(false)
                    }
                },
                startWidget = {
                    Icon(
                        painter = painterResource(drawables.build),
                        contentDescription = stringResource(strings.debug_options),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                },
                enabled = InbuiltFeatures.debugMode.supported,
            )

            SettingsToggle(
                label = stringResource(InbuiltFeatures.terminal.nameRes),
                default = InbuiltFeatures.terminal.state.value,
                sideEffect = { InbuiltFeatures.terminal.setEnable(it) },
                startWidget = {
                    Icon(
                        painter = painterResource(drawables.terminal),
                        contentDescription = stringResource(strings.terminal),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                },
                isEnabled = InbuiltFeatures.terminal.supported,
            )

            SettingsToggle(
                label = stringResource(InbuiltFeatures.extensions.nameRes),
                default = InbuiltFeatures.extensions.state.value,
                sideEffect = { InbuiltFeatures.extensions.setEnable(it) },
                startWidget = {
                    Icon(
                        painter = painterResource(drawables.extension),
                        contentDescription = stringResource(strings.ext),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                },
                isEnabled = InbuiltFeatures.extensions.supported,
            )

            SettingsToggle(
                label = stringResource(InbuiltFeatures.git.nameRes),
                default = InbuiltFeatures.git.state.value,
                sideEffect = { InbuiltFeatures.git.setEnable(it) },
                startWidget = {
                    Icon(
                        painter = painterResource(drawables.git),
                        contentDescription = stringResource(strings.git),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                },
                isEnabled = InbuiltFeatures.git.supported,
            )
        }

        PreferenceGroup(heading = stringResource(strings.backup)) {
            SettingsToggle(
                label = stringResource(id = strings.backup),
                description = stringResource(id = strings.settings_backup_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    activity.fileManager.createNewFile("application/json", "xed-settings.json") { fileObject ->
                        if (fileObject == null) return@createNewFile
                        scope.launch(Dispatchers.IO) {
                            try {
                                val json = gson.toJson(Preference.getAll())
                                fileObject.getOutPutStream(false).use { outputStream ->
                                    outputStream.write(json.toByteArray())
                                }
                                toast(strings.export_successful)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                toast(strings.export_failed)
                            }
                        }
                    }
                },
            )
            SettingsToggle(
                label = stringResource(id = strings.restore),
                description = stringResource(id = strings.settings_restore_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    activity.fileManager.requestOpenFile("application/json") { uri ->
                        if (uri == null) return@requestOpenFile
                        scope.launch(Dispatchers.IO) {
                            try {
                                val type = object : TypeToken<Map<String, Any>>() {}.type
                                val content = uri.toFileObject(true).readText()
                                val json: Map<String, Any> = gson.fromJson(content, type)

                                Preference.clearData()
                                json.forEach { (key, value) ->
                                    val expectedType = Preference.preferenceTypes[key]

                                    val fixedValue =
                                        when (expectedType) {
                                            Float::class -> (value as Number).toFloat()
                                            Int::class -> (value as Number).toInt()
                                            Long::class -> (value as Number).toLong()
                                            Boolean::class -> value as Boolean
                                            String::class -> value as String
                                            else -> value
                                        }

                                    Preference.put(key, fixedValue)
                                }
                                ReactiveSettings.update()

                                // Update theme in the UI if the setting changed
                                withContext(Dispatchers.Main) {
                                    AppCompatDelegate.setDefaultNightMode(Settings.theme_mode)
                                    dynamicTheme.value = Settings.monet
                                    amoled.value = Settings.amoled
                                    currentTheme.value = null
                                    refreshEditors()
                                }

                                toast(strings.import_successful)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                toast(strings.import_failed)
                            }
                        }
                    }
                },
            )
        }
    }
}
