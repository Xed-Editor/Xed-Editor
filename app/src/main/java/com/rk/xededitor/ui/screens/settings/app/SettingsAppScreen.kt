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
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.components.BottomSheetContent
import kotlinx.coroutines.launch
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory

@Composable
fun SettingsAppScreen() {
    // State variables
    val context = LocalContext.current
    var isOled by remember { mutableStateOf(PreferencesData.isOled()) }
    var isMonet by remember { mutableStateOf(PreferencesData.isMonet()) }
    var checkForUpdates by remember { mutableStateOf(PreferencesData.getBoolean(PreferencesKeys.CHECK_UPDATE, true)) }
    val showDayNightBottomSheet = remember { mutableStateOf(false) }
    
    PreferenceLayout(label = stringResource(id = R.string.app), backArrowVisible = true) {
        ThemeModePreference(showDayNightBottomSheet)
        OledPreference(isOled) { isEnabled ->
            isOled = isEnabled
            PreferencesData.setBoolean(PreferencesKeys.OLED, isOled)
            rkUtils.toast(rkUtils.getString(R.string.restart_required))
        }
        CheckForUpdatesPreference(checkForUpdates) { isEnabled ->
            checkForUpdates = isEnabled
            PreferencesData.setBoolean(PreferencesKeys.CHECK_UPDATE, checkForUpdates)
        }
        MonetPreference(isMonet) { isEnabled ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                isMonet = isEnabled
                PreferencesData.setBoolean(PreferencesKeys.MONET, isMonet)
            }
        }
        if (showDayNightBottomSheet.value) {
            DayNightDialog(showBottomSheet = showDayNightBottomSheet, context = context)
        }
    }
}

@Composable
fun ThemeModePreference(showBottomSheet: MutableState<Boolean>) {
    PreferenceCategory(
        label = stringResource(id = R.string.theme_mode),
        description = stringResource(id = R.string.theme_mode_desc),
        iconResource = R.drawable.theme_mode,
        onNavigate = { showBottomSheet.value = true }
    )
}

@Composable
fun OledPreference(isOled: Boolean, onToggle: (Boolean) -> Unit) {
    PreferenceCategory(
        label = stringResource(id = R.string.oled),
        description = stringResource(id = R.string.oled_desc),
        iconResource = R.drawable.dark_mode,
        onNavigate = { onToggle(!isOled) },
        endWidget = {
            Switch(
                modifier = Modifier.padding(12.dp).height(24.dp),
                checked = isOled,
                onCheckedChange = { onToggle(!isOled) }
            )
        }
    )
}

@Composable
fun CheckForUpdatesPreference(checkForUpdates: Boolean, onToggle: (Boolean) -> Unit) {
    PreferenceCategory(
        label = stringResource(R.string.check_for_updates),
        description = stringResource(R.string.check_for_updates_desc),
        iconResource = R.drawable.android,
        onNavigate = { onToggle(!checkForUpdates) },
        endWidget = {
            Switch(
                modifier = Modifier.padding(12.dp).height(24.dp),
                checked = checkForUpdates,
                onCheckedChange = { onToggle(!checkForUpdates) }
            )
        }
    )
}

@Composable
fun MonetPreference(isMonet: Boolean, onToggle: (Boolean) -> Unit) {
    val isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    PreferenceCategory(
        label = stringResource(id = R.string.monet),
        description = stringResource(id = R.string.monet_desc),
        iconResource = R.drawable.palette,
        enabled = isEnabled,
        onNavigate = { if (isEnabled) onToggle(!isMonet) },
        endWidget = {
            Switch(
                modifier = Modifier.padding(12.dp).height(24.dp),
                checked = isMonet,
                enabled = isEnabled,
                onCheckedChange = { if (isEnabled) onToggle(!isMonet) }
            )
        }
    )
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
    val modeLabels = listOf(context.getString(R.string.light_mode), context.getString(R.string.dark_mode), context.getString(R.string.auto_mode))
    
    if (showBottomSheet.value) {
        ModalBottomSheet(onDismissRequest = { showBottomSheet.value = false }, sheetState = bottomSheetState) {
            BottomSheetContent(
                title = { Text(text = stringResource(id = R.string.select_theme_mode)) },
                buttons = {
                    OutlinedButton(onClick = { coroutineScope.launch { bottomSheetState.hide(); showBottomSheet.value = false } }) {
                        Text(text = stringResource(id = R.string.cancel))
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
