package com.rk.xededitor.ui.screens.settings.app

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.components.BottomSheetContent
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.launch
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory

@Composable
fun SettingsAppScreen() {
    
    val showDayNightBottomSheet = remember { mutableStateOf(false) }
    
    PreferenceGroup(heading = stringResource(strings.app)) {
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
            default = false,
            isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
    }
    
    if (showDayNightBottomSheet.value) {
        DayNightDialog(showBottomSheet = showDayNightBottomSheet, context = LocalContext.current)
    }
    
    
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayNightDialog(showBottomSheet: MutableState<Boolean>, context: Context) {
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
                                AppCompatDelegate.setDefaultNightMode(selectedMode)
                                coroutineScope.launch { bottomSheetState.hide(); showBottomSheet.value = false }
                            },
                            startWidget = { RadioButton(selected = selectedMode == mode, onClick = null) }
                        )
                    }
                }
            }
        }
    }
}