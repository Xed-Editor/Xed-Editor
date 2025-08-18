package com.rk.xededitor.ui.screens.settings.theme

import android.content.Context
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rk.DefaultScope
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.file.FileManager
import com.rk.file.UriWrapper
import com.rk.file.child
import com.rk.file.themeDir
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.ui.activities.main.MainActivity
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.components.BottomSheetContent
import com.rk.xededitor.ui.components.SettingsToggle
import com.rk.xededitor.ui.theme.Theme
import com.rk.xededitor.ui.theme.amoled
import com.rk.xededitor.ui.theme.currentTheme
import com.rk.xededitor.ui.theme.defaultTheme
import com.rk.xededitor.ui.theme.dynamicTheme
import com.rk.xededitor.ui.theme.installFromFile
import com.rk.xededitor.ui.theme.updateThemes
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

val themes = mutableStateListOf<Theme>()

@Composable
fun ThemeScreen(modifier: Modifier = Modifier) {
    val showDayNightBottomSheet = remember { mutableStateOf(false) }
    val monetState = remember { mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Settings.monet) }
    val amoledState = remember { mutableStateOf(Settings.amoled) }


    PreferenceLayout(label = stringResource(strings.themes), fab = {
        ExtendedFloatingActionButton(onClick = {
            SettingsActivity.instance?.fileManager?.requestOpenFile(mimeType = "application/json"){
                DefaultScope.launch{
                    installFromFile(UriWrapper(it!!,false))
                    updateThemes()
                }
            }
        }, icon = {
            Icon(imageVector = Icons.Outlined.Add,null)
        }, text = {
            Text(stringResource(strings.add_theme))
        })
    }) {

        PreferenceGroup(heading = stringResource(strings.theme_settings)) {
            SettingsToggle(label = stringResource(id = strings.theme_mode),
                description = stringResource(id = strings.theme_mode_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    showDayNightBottomSheet.value = true
                })



            SettingsToggle(label = stringResource(id = strings.oled),
                description = stringResource(id = strings.oled_desc),
                default = Settings.amoled,
                state = amoledState,
                sideEffect = {
                    Settings.amoled = it
                    amoled.value = it
                    updateThemes()
                })

            SettingsToggle(
                label = stringResource(id = strings.monet),
                description = stringResource(id = strings.monet_desc),
                default = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Settings.monet,
                isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                state = monetState,
                sideEffect = {
                    Settings.monet = it
                    dynamicTheme.value = it
                    updateThemes()
                }

            )
        }


        PreferenceGroup(heading = stringResource(strings.themes)) {
            if (themes.isEmpty()){
                SettingsToggle(label = "No themes found", description = null, showSwitch = false, default = false)
            }else{
                themes.forEach{ theme ->
                    SettingsToggle(
                        isEnabled = !dynamicTheme.value,
                        label = theme.name, description = null, showSwitch = false, default = false, startWidget = {
                        RadioButton(
                            enabled = !dynamicTheme.value,
                            selected = currentTheme.value?.id == theme.id, onClick = {
                            currentTheme.value = theme
                            Settings.theme = theme.id
                        })
                    }, sideEffect = {
                        currentTheme.value = theme
                        Settings.theme = theme.id
                    }, endWidget = {
                        if (theme != defaultTheme){
                            IconButton(onClick = {
                                themeDir().child(theme.name).delete()
                                currentTheme.value = defaultTheme
                                Settings.theme = defaultTheme.id
                                themes.remove(theme)
                            }) {
                                Icon(imageVector = Icons.Outlined.Delete,null)
                            }
                        }

                    })
                }
            }

        }

        if (showDayNightBottomSheet.value) {
            DayNightDialog(
                showBottomSheet = showDayNightBottomSheet,
                context = LocalContext.current,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayNightDialog(
    showBottomSheet: MutableState<Boolean>, context: Context,
) {
    val bottomSheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    var selectedMode by remember {
        mutableIntStateOf(Settings.default_night_mode)
    }

    val modes = listOf(
        AppCompatDelegate.MODE_NIGHT_NO,
        AppCompatDelegate.MODE_NIGHT_YES,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )
    val modeLabels = listOf(
        context.getString(strings.light_mode),
        context.getString(strings.dark_mode),
        context.getString(strings.auto_mode)
    )

    if (showBottomSheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet.value = false }, sheetState = bottomSheetState
        ) {
            BottomSheetContent(title = { Text(text = stringResource(id = strings.theme_mode)) },
                buttons = {
                    OutlinedButton(onClick = {
                        coroutineScope.launch {
                            bottomSheetState.hide(); showBottomSheet.value = false
                        }
                    }) {
                        Text(text = stringResource(id = strings.cancel))
                    }
                }) {
                LazyColumn {
                    itemsIndexed(modes) { index, mode ->
                        PreferenceTemplate(title = { Text(text = modeLabels[index]) },
                            modifier = Modifier.clickable {
                                selectedMode = mode
                                Settings.default_night_mode = selectedMode
                                AppCompatDelegate.setDefaultNightMode(selectedMode)
                                coroutineScope.launch {
                                    bottomSheetState.hide(); showBottomSheet.value = false;
                                }
                                //toast(strings.restart_required)
                            },
                            startWidget = {
                                RadioButton(
                                    selected = selectedMode == mode, onClick = null
                                )
                            })
                    }
                }
            }
        }
    }
}