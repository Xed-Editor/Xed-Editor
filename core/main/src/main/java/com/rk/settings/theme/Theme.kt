package com.rk.settings.theme

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.App.Companion.iconPackManager
import com.rk.App.Companion.themeManager
import com.rk.DefaultScope
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.BottomSheetContent
import com.rk.components.SettingsItem
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.events.AppEvent
import com.rk.events.Events
import com.rk.file.child
import com.rk.file.themeDir
import com.rk.icons.pack.currentIconPack
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.editor.refreshEditors
import com.rk.theme.blueberry
import com.rk.theme.builtInThemes
import com.rk.theme.currentTheme
import kotlinx.coroutines.launch

@Composable
fun ThemeScreen(navController: NavController, modifier: Modifier = Modifier) {
    val showDayNightBottomSheet = remember { mutableStateOf(false) }
    val monetState = remember { mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Settings.monet) }
    val amoledState = remember { mutableStateOf(Settings.amoled) }

    PreferenceLayout(label = stringResource(strings.themes)) {
        PreferenceGroup(heading = stringResource(strings.theme_settings)) {
            SettingsItem(
                label = stringResource(id = strings.theme_mode),
                description = stringResource(id = strings.theme_mode_desc),
                showSwitch = false,
                default = false,
                sideEffect = { showDayNightBottomSheet.value = true },
            )

            SettingsItem(
                label = stringResource(id = strings.oled),
                description = stringResource(id = strings.oled_desc),
                default = Settings.amoled,
                state = amoledState,
                sideEffect = {
                    Settings.amoled = it
                    refreshEditors()
                },
            )

            SettingsItem(
                label = stringResource(id = strings.monet),
                description = stringResource(id = strings.monet_desc),
                default = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Settings.monet,
                isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                state = monetState,
                sideEffect = {
                    Settings.monet = it
                    refreshEditors()
                },
            )
        }

        PreferenceGroup(heading = stringResource(strings.themes)) {
            themeManager.loadedThemes.forEach { theme ->
                SettingsItem(
                    isEnabled = !Settings.monet,
                    label = theme.name,
                    description = null,
                    showSwitch = false,
                    default = false,
                    startWidget = {
                        RadioButton(
                            modifier = Modifier.padding(start = 16.dp),
                            enabled = !Settings.monet,
                            selected = currentTheme.value.id == theme.id,
                            onClick = null,
                        )
                    },
                    sideEffect = {
                        val oldTheme = currentTheme.value
                        Settings.theme = theme.id
                        refreshEditors()
                        DefaultScope.launch { Events.publish(AppEvent.ThemeChanged(theme, oldTheme)) }
                    },
                    endWidget = {
                        if (!builtInThemes.contains(theme)) {
                            IconButton(
                                onClick = {
                                    if (currentTheme.value.id == theme.id) {
                                        val oldTheme = currentTheme.value
                                        Settings.theme = blueberry.id
                                        refreshEditors()
                                        DefaultScope.launch {
                                            Events.publish(AppEvent.ThemeChanged(blueberry, oldTheme))
                                        }
                                    }

                                    themeDir().child(theme.id).deleteRecursively()
                                    themeManager.uninstallTheme(theme)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(strings.delete),
                                )
                            }
                        }
                    },
                )
            }

            SettingsItem(
                label = stringResource(strings.browse_themes),
                description = null,
                showSwitch = false,
                default = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp),
                        painter = painterResource(drawables.arrow_outward),
                        contentDescription = null,
                    )
                },
                sideEffect = {
                    navController.navigate("${SettingsRoutes.Extensions.route}?category=themes")
                },
            )
        }

        PreferenceGroup(heading = stringResource(strings.icon_packs)) {
            SettingsItem(
                label = "Simple Icons (${stringResource(strings.default_option)})",
                description = null,
                showSwitch = false,
                default = false,
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 16.dp),
                        selected = currentIconPack.value == null,
                        onClick = null,
                    )
                },
                sideEffect = {
                    val oldIconPack = currentIconPack.value
                    currentIconPack.value = null
                    Settings.icon_pack = ""

                    DefaultScope.launch {
                        Events.publish(AppEvent.IconPackChanged(null, oldIconPack))
                    }
                },
            )

            iconPackManager.localIconPacks.forEach { (id, iconPack) ->
                val iconPackManifest = iconPack.manifest

                SettingsItem(
                    label = iconPackManifest.name,
                    description = null,
                    showSwitch = false,
                    default = false,
                    startWidget = {
                        RadioButton(
                            modifier = Modifier.padding(start = 16.dp),
                            selected = currentIconPack.value?.manifest?.id == id,
                            onClick = null,
                        )
                    },
                    sideEffect = {
                        val oldIconPack = currentIconPack.value
                        currentIconPack.value = iconPack
                        Settings.icon_pack = id

                        DefaultScope.launch {
                            Events.publish(AppEvent.IconPackChanged(iconPack, oldIconPack))
                        }
                    },
                    endWidget = {
                        IconButton(
                            onClick = {
                                if (currentIconPack.value?.manifest?.id == id) {
                                    val oldIconPack = currentIconPack.value
                                    currentIconPack.value = null
                                    Settings.icon_pack = ""

                                    DefaultScope.launch {
                                        Events.publish(AppEvent.IconPackChanged(null, oldIconPack))
                                    }
                                }

                                iconPackManager.uninstallIconPack(id)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(strings.delete),
                            )
                        }
                    },
                )
            }

            SettingsItem(
                label = stringResource(strings.browse_icon_packs),
                description = null,
                showSwitch = false,
                default = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp),
                        painter = painterResource(drawables.arrow_outward),
                        contentDescription = null,
                    )
                },
                sideEffect = {
                    navController.navigate("${SettingsRoutes.Extensions.route}?category=icon_packs")
                },
            )
        }

        if (showDayNightBottomSheet.value) {
            DayNightDialog(showBottomSheet = showDayNightBottomSheet, context = LocalContext.current)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayNightDialog(showBottomSheet: MutableState<Boolean>, context: Context) {
    val bottomSheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    var selectedMode by remember { mutableIntStateOf(Settings.theme_mode) }

    val modes =
        listOf(
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        )
    val modeLabels =
        listOf(
            context.getString(strings.light_mode),
            context.getString(strings.dark_mode),
            context.getString(strings.auto_mode),
        )

    if (showBottomSheet.value) {
        ModalBottomSheet(onDismissRequest = { showBottomSheet.value = false }, sheetState = bottomSheetState) {
            BottomSheetContent(
                title = { Text(text = stringResource(id = strings.theme_mode)) },
                buttons = {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                bottomSheetState.hide()
                                showBottomSheet.value = false
                            }
                        }
                    ) {
                        Text(text = stringResource(id = strings.cancel))
                    }
                },
            ) {
                LazyColumn {
                    itemsIndexed(modes) { index, mode ->
                        PreferenceTemplate(
                            title = { Text(text = modeLabels[index]) },
                            modifier =
                                Modifier.clickable {
                                    selectedMode = mode
                                    Settings.theme_mode = selectedMode
                                    AppCompatDelegate.setDefaultNightMode(selectedMode)
                                    coroutineScope.launch {
                                        bottomSheetState.hide()
                                        showBottomSheet.value = false
                                    }
                                },
                            startWidget = { RadioButton(selected = selectedMode == mode, onClick = null) },
                        )
                    }
                }
            }
        }
    }
}
