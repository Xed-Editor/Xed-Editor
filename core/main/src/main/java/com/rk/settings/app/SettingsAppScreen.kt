package com.rk.settings.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
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
import com.rk.components.NextScreenCard
import com.rk.components.SettingsItem
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.file.toFileObject
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.settings.editor.refreshEditors
import com.rk.theme.amoled
import com.rk.theme.currentTheme
import com.rk.theme.dynamicTheme
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rk.feature.FeatureRegistry
import com.rk.utils.application

@Composable
fun SettingsAppScreen(activity: SettingsActivity, navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.app), backArrowVisible = true) {
        val scope = rememberCoroutineScope()
        val gson = remember { GsonBuilder().setPrettyPrinting().create() }

        PreferenceGroup {
            SettingsItem(
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

            SettingsItem(
                label = stringResource(strings.check_for_updates),
                description = stringResource(strings.check_for_updates_desc),
                default = Settings.check_for_update,
                sideEffect = { Settings.check_for_update = it },
            )

            SettingsItem(
                label = stringResource(strings.fullscreen),
                description = stringResource(strings.fullscreen_desc),
                default = Settings.fullscreen,
                sideEffect = { Settings.fullscreen = it },
            )

            SettingsItem(
                label = stringResource(strings.smart_toolbar),
                description = stringResource(strings.smart_toolbar_desc),
                default = Settings.smart_toolbar,
                sideEffect = { Settings.smart_toolbar = it },
            )

            SettingsItem(
                label = stringResource(strings.confirm_exit_dialog),
                description = stringResource(strings.confirm_exit_dialog_desc),
                default = Settings.confirm_exit,
                sideEffect = { Settings.confirm_exit = it },
            )



            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                var hasManageExternalStorageDeclared by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val app = application ?: return@LaunchedEffect
                    val pm = app.packageManager

                    val pkgInfo = pm.getPackageInfo(
                        app.packageName,
                        PackageManager.GET_PERMISSIONS
                    )

                    hasManageExternalStorageDeclared =
                        pkgInfo.requestedPermissions?.any {
                            it == android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
                        } ?: false
                }

                SettingsItem(
                    label = stringResource(strings.manage_storage),
                    description = stringResource(strings.manage_storage_desc),
                    showSwitch = false,
                    isEnabled = hasManageExternalStorageDeclared,
                    default = false,
                    endWidget = {
                        Icon(
                            modifier = Modifier.padding(16.dp),
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    sideEffect = {
                        val intent =
                            Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = "package:${activity.packageName}".toUri()
                        activity.startActivity(intent)
                    },
                )
            }

            NextScreenCard(
                label = stringResource(strings.manage_app_font),
                description = stringResource(strings.manage_app_font),
                route = SettingsRoutes.AppFontScreen,
            )
        }

        PreferenceGroup(heading = stringResource(strings.feature_toggles)) {
            FeatureRegistry.toggles.forEach { toggle ->
                BasicToggle(
                    label = stringResource(toggle.nameRes),
                    checked = toggle.state.value,
                    onSwitch = { checked ->
                        if (toggle.onSwitch != null) {
                            toggle.onSwitch.invoke(activity, checked) { ok ->
                                toggle.setEnable(ok)
                            }
                        } else {
                            toggle.setEnable(checked)
                        }
                    },
                    startWidget = {
                        Icon(
                            painter = painterResource(toggle.iconRes),
                            contentDescription = stringResource(toggle.nameRes),
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                )
            }
        }

        PreferenceGroup(heading = stringResource(strings.backup)) {
            SettingsItem(
                label = stringResource(id = strings.backup),
                description = stringResource(id = strings.settings_backup_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    activity.fileManager.createNewFile(
                        "application/json",
                        "xed-settings.json"
                    ) { fileObject ->
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
            SettingsItem(
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
