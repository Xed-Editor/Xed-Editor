package com.rk.xededitor.ui.screens.settings.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.smoothTabs
import com.rk.xededitor.MainActivity.tabs.editor.AutoSaver
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.components.InputDialog
import com.rk.xededitor.ui.components.SettingsToggle
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory

@Composable
fun SettingsEditorScreen() {
    PreferenceLayout(label = stringResource(id = R.string.editor), backArrowVisible = true) {
        val context = LocalContext.current
        
        var showAutoSaveDialog by remember { mutableStateOf(false) }
        var showTextSizeDialog by remember { mutableStateOf(false) }
        var showTabSizeDialog by remember { mutableStateOf(false) }
        var autoSaveTimeValue by remember {
            mutableStateOf(PreferencesData.getString(PreferencesKeys.AUTO_SAVE_TIME_VALUE, "10000"))
        }
        var textSizeValue by remember {
            mutableStateOf(PreferencesData.getString(PreferencesKeys.TEXT_SIZE, "14"))
        }
        var tabSizeValue by remember {
            mutableStateOf(PreferencesData.getString(PreferencesKeys.TAB_SIZE, "4"))
        }
        

        
        PreferenceGroup(heading = "content") {
            SettingsToggle(label = stringResource(id = strings.ww),
                description = stringResource(id = strings.ww_desc),
                iconRes = drawable.reorder,
                key = PreferencesKeys.WORD_WRAP_ENABLED,
                default = false,
                sideEffect = {
                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).editor?.isWordwrap = it
                        }
                    }
                })
            SettingsToggle(label = stringResource(strings.txt_ww),
                description = stringResource(strings.txt_ww_desc),
                iconRes = drawable.reorder,
                key = PreferencesKeys.WORD_WRAP_TXT,
                default = false,
                sideEffect = {
                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).apply {
                                if (file?.name?.endsWith(".txt") == true) {
                                    if (editor?.isWordwrap!!.not()) {
                                        editor?.isWordwrap = it
                                    }
                                }
                            }
                        }
                    }
                })
            SettingsToggle(label = "Anti word breaking",
                description = "don't break words in word wrap",
                iconRes = drawable.reorder,
                key = PreferencesKeys.ANTI_WORD_BREAKING,
                default = true,
                sideEffect = {
                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).editor?.setWordwrap(
                                PreferencesData.getBoolean(
                                    PreferencesKeys.WORD_WRAP_ENABLED, false
                                ), it
                            )
                        }
                    }
                })
        }
        
        
        
        PreferenceGroup(heading = "editor") {
            SettingsToggle(label = stringResource(id = strings.cursor_anim),
                description = stringResource(id = strings.cursor_anim_desc),
                iconRes = drawable.animation,
                key = PreferencesKeys.CURSOR_ANIMATION_ENABLED,
                default = false,
                sideEffect = {
                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).editor?.isCursorAnimationEnabled = it
                        }
                    }
                })
            SettingsToggle(label = stringResource(id = strings.show_line_number),
                description = stringResource(id = strings.show_line_number),
                iconRes = drawable.linenumbers,
                key = PreferencesKeys.SHOW_LINE_NUMBERS,
                default = true,
                sideEffect = {
                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).editor?.isLineNumberEnabled = it
                        }
                    }
                })
            SettingsToggle(label = stringResource(id = strings.show_suggestions),
                description = stringResource(id = strings.show_suggestions),
                iconRes = drawable.baseline_font_download_24,
                key = PreferencesKeys.SHOW_SUGGESTIONS,
                default = false,
                sideEffect = {
                    MainActivity.activityRef.get()?.adapter!!.tabFragments.values.forEach { f ->
                        if (f.get()?.fragment is EditorFragment) {
                            (f.get()?.fragment as EditorFragment).editor?.showSuggestions(it)
                        }
                    }
                })
            SettingsToggle(label = stringResource(id = strings.pin_line_number),
                description = stringResource(id = strings.pin_line_number),
                iconRes = drawable.linenumbers,
                key = PreferencesKeys.PIN_LINE_NUMBER,
                default = false,
                sideEffect = {
                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).editor?.setPinLineNumber(it)
                        }
                    }
                })
            SettingsToggle(
                label = stringResource(id = strings.editor_font),
                description = stringResource(id = strings.editor_font_desc),
                iconRes = drawable.baseline_font_download_24,
                key = PreferencesKeys.EDITOR_FONT,
                default = false,
            )
            PreferenceCategory(
                label = stringResource(id = strings.text_size),
                description = stringResource(id = strings.text_size_desc),
                iconResource = drawable.reorder,
                onNavigate = { showTextSizeDialog = true },
            )
        }
        
        
        
        PreferenceGroup(heading = "misc.") {
            SettingsToggle(label = stringResource(id = strings.extra_keys),
                description = stringResource(id = strings.extra_keys_desc),
                iconRes = drawable.double_arrows,
                key = PreferencesKeys.SHOW_ARROW_KEYS,
                default = true,
                sideEffect = {
                    MainActivity.activityRef.get()?.let { activity ->
                        if (activity.tabViewModel.fragmentFiles.isEmpty()) {
                            return@let
                        }
                        
                        MainActivity.activityRef.get()?.adapter?.tabFragments?.values?.forEach { f ->
                            if (f.get()?.fragment is EditorFragment) {
                                (f.get()?.fragment as EditorFragment).showArrowKeys(it)
                            }
                        }
                    }
                })
            SettingsToggle(label = stringResource(id = strings.smooth_tabs),
                description = stringResource(id = strings.smooth_tab_desc),
                iconRes = drawable.animation,
                key = PreferencesKeys.VIEWPAGER_SMOOTH_SCROLL,
                default = false,
                sideEffect = {
                    smoothTabs = it
                })
            SettingsToggle(
                label = stringResource(id = strings.keepdl),
                description = stringResource(id = strings.drawer_lock_desc),
                iconRes = drawable.lock,
                key = PreferencesKeys.KEEP_DRAWER_LOCKED,
                default = false,
            )
            SettingsToggle(
                label = stringResource(id = strings.auto_save),
                description = stringResource(id = strings.auto_save_desc),
                iconRes = drawable.save,
                key = PreferencesKeys.AUTO_SAVE,
                default = false,
            )
            
            SettingsToggle(
                label = stringResource(strings.sora_s),
                description = stringResource(strings.sora_s_desc),
                iconRes = drawable.search,
                key = PreferencesKeys.USE_SORA_SEARCH,
                default = false,
            )
            PreferenceCategory(
                label = stringResource(id = strings.auto_save_time),
                description = stringResource(id = strings.auto_save_time_desc),
                iconResource = drawable.save,
                onNavigate = { showAutoSaveDialog = true },
            )
            
            PreferenceCategory(
                label = stringResource(id = strings.tab_size),
                description = stringResource(id = strings.tab_size_desc),
                iconResource = drawable.double_arrows,
                onNavigate = { showTabSizeDialog = true },
            )
        }
        
        if (showAutoSaveDialog) {
            InputDialog(
                title = stringResource(id = R.string.auto_save_time),
                inputLabel = stringResource(id = R.string.intervalinMs),
                inputValue = autoSaveTimeValue,
                onInputValueChange = { autoSaveTimeValue = it },
                onConfirm = {
                    if (autoSaveTimeValue.any { !it.isDigit() }) {
                        rkUtils.toast(context.getString(R.string.inavalid_v))
                    } else if (autoSaveTimeValue.toInt() < 1000) {
                        rkUtils.toast(context.getString(R.string.v_small))
                    } else {
                        PreferencesData.setString(
                            PreferencesKeys.AUTO_SAVE_TIME_VALUE,
                            autoSaveTimeValue,
                        )
                        AutoSaver.delayTime = autoSaveTimeValue.toLong()
                    }
                    showAutoSaveDialog = false
                },
                onDismiss = { showAutoSaveDialog = false },
            )
        }
        
        if (showTextSizeDialog) {
            InputDialog(
                title = stringResource(id = R.string.text_size),
                inputLabel = stringResource(id = R.string.text_size),
                inputValue = textSizeValue,
                onInputValueChange = { textSizeValue = it },
                onConfirm = {
                    if (textSizeValue.any { !it.isDigit() }) {
                        rkUtils.toast(context.getString(R.string.inavalid_v))
                    } else if (textSizeValue.toInt() > 32) {
                        rkUtils.toast(context.getString(R.string.v_large))
                    } else if (textSizeValue.toInt() < 8) {
                        rkUtils.toast(context.getString(R.string.v_small))
                    } else {
                        PreferencesData.setString(PreferencesKeys.TEXT_SIZE, textSizeValue)
                        MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                            if (f.value.get()?.fragment is EditorFragment) {
                                (f.value.get()?.fragment as EditorFragment).editor?.setTextSize(textSizeValue.toFloat())
                            }
                            
                        }
                    }
                    showTextSizeDialog = false
                },
                onDismiss = { showTextSizeDialog = false },
            )
        }
        if (showTabSizeDialog) {
            InputDialog(
                title = stringResource(id = R.string.tab_size),
                inputLabel = stringResource(id = R.string.tab_size),
                inputValue = tabSizeValue,
                onInputValueChange = { tabSizeValue = it },
                onConfirm = {
                    if (tabSizeValue.any { !it.isDigit() }) {
                        rkUtils.toast(context.getString(R.string.inavalid_v))
                    } else if (tabSizeValue.toInt() > 16) {
                        rkUtils.toast(context.getString(R.string.v_large))
                    }
                    PreferencesData.setString(PreferencesKeys.TAB_SIZE, tabSizeValue)
                    
                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).editor?.tabWidth = tabSizeValue.toInt()
                        }
                        
                    }
                    showTabSizeDialog = false
                },
                onDismiss = { showTabSizeDialog = false },
            )
        }
        
    }
}
