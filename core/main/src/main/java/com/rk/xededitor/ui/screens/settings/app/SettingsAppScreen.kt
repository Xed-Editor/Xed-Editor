package com.rk.xededitor.ui.screens.settings.app

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.components.BottomSheetContent
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import java.time.Duration

@Composable
fun SettingsAppScreen(activity: SettingsActivity) {
    PreferenceLayout(label = stringResource(id = strings.app), backArrowVisible = true) {
        
        val showDayNightBottomSheet = remember { mutableStateOf(false) }
        
        PreferenceGroup {
            SettingsToggle(
                label = stringResource(id = strings.theme_mode),
                description = stringResource(id = strings.theme_mode_desc),
                showSwitch = false,
                sideEffect = {
                    showDayNightBottomSheet.value = true
                }
            )
            
            
            SettingsToggle(
                label = stringResource(id = strings.oled),
                description = stringResource(id = strings.oled_desc),
                key = PreferencesKeys.OLED,
                default = false,
                sideEffect = {
                    rkUtils.toast(rkUtils.getString(strings.restart_required))
                }
            )
            
            
            SettingsToggle(
                label = stringResource(strings.check_for_updates),
                description = stringResource(strings.check_for_updates_desc),
                key = PreferencesKeys.CHECK_UPDATE,
                default = false,
            )
            
            SettingsToggle(
                label = stringResource(id = strings.monet),
                description = stringResource(id = strings.monet_desc),
                key = PreferencesKeys.MONET,
                default = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            )
        }
        
        if (showDayNightBottomSheet.value) {
            DayNightDialog(showBottomSheet = showDayNightBottomSheet, context = LocalContext.current,activity = activity)
        }
    }
    
    
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayNightDialog(showBottomSheet: MutableState<Boolean>, context: Context,activity: SettingsActivity) {
    val bottomSheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    var selectedMode by remember {
        mutableIntStateOf(
            PreferencesData.getString(PreferencesKeys.DEFAULT_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString()).toInt()
        )
    }
    
    val modes = listOf(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    val modeLabels = listOf(context.getString(strings.light_mode), context.getString(strings.dark_mode), context.getString(strings.auto_mode))
    
    if (showBottomSheet.value) {
        ModalBottomSheet(onDismissRequest = { showBottomSheet.value = false }, sheetState = bottomSheetState) {
            BottomSheetContent(
                title = { Text(text = stringResource(id = strings.select_theme_mode)) },
                buttons = {
                    OutlinedButton(onClick = { coroutineScope.launch { bottomSheetState.hide(); showBottomSheet.value = false } }) {
                        Text(text = stringResource(id = strings.cancel))
                    }
                }
            ) {
                LazyColumn {
                    itemsIndexed(modes) { index, mode ->
                        PreferenceTemplate(
                            title = { Text(text = modeLabels[index]) },
                            modifier = Modifier.clickable {
                                selectedMode = mode
                                PreferencesData.setString(PreferencesKeys.DEFAULT_NIGHT_MODE, selectedMode.toString())
                                //AppCompatDelegate.setDefaultNightMode(selectedMode)
                                coroutineScope.launch { bottomSheetState.hide(); showBottomSheet.value = false; }
                                rkUtils.toast(strings.restart_required.getString())
                            },
                            startWidget = { RadioButton(selected = selectedMode == mode, onClick = null) }
                        )
                    }
                }
            }
        }
    }
}