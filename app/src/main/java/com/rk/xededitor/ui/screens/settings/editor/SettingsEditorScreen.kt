package com.rk.xededitor.ui.screens.settings.editor

import android.widget.RelativeLayout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.AutoSaver
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.file.smoothTabs
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.getString
import com.rk.xededitor.ui.components.InputDialog
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory

@Composable
fun SettingsEditorScreen() {
    PreferenceLayout(label = stringResource(id = R.string.editor), backArrowVisible = true) {
        var _smoothTabs by remember {
            mutableStateOf(
                PreferencesData.getBoolean(PreferencesKeys.VIEWPAGER_SMOOTH_SCROLL, false)
            )
        }
        var wordwrap by remember {
            mutableStateOf(PreferencesData.getBoolean(PreferencesKeys.WORD_WRAP_ENABLED, false))
        }
        var drawerLock by remember {
            mutableStateOf(PreferencesData.getBoolean(PreferencesKeys.KEEP_DRAWER_LOCKED, false))
        }
        var diagonalScroll by remember {
            mutableStateOf(PreferencesData.getBoolean(PreferencesKeys.DIAGONAL_SCROLL, true))
        }
        var cursorAnimation by remember {
            mutableStateOf(
                PreferencesData.getBoolean(PreferencesKeys.CURSOR_ANIMATION_ENABLED, true)
            )
        }
        var showLineNumber by remember {
            mutableStateOf(PreferencesData.getBoolean(PreferencesKeys.SHOW_LINE_NUMBERS, true))
        }
        var pinLineNumber by remember {
            mutableStateOf(PreferencesData.getBoolean(PreferencesKeys.PIN_LINE_NUMBER, false))
        }
        var showArrowKeys by remember {
            mutableStateOf(PreferencesData.getBoolean(PreferencesKeys.SHOW_ARROW_KEYS, false))
        }
        var autoSave by remember {
            mutableStateOf(PreferencesData.getBoolean(PreferencesKeys.AUTO_SAVE, false))
        }
        var editorFont by remember {
            mutableStateOf(PreferencesData.getBoolean(PreferencesKeys.EDITOR_FONT, false))
        }
        var showSuggestions by remember {
            mutableStateOf(PreferencesData.getBoolean(PreferencesKeys.SHOW_SUGGESTIONS, false))
        }

        val context = LocalContext.current

        PreferenceCategory(
            label = stringResource(id = R.string.smooth_tabs),
            description = stringResource(id = R.string.smooth_tab_desc),
            iconResource = R.drawable.animation,
            onNavigate = {
                _smoothTabs = !_smoothTabs
                PreferencesData.setBoolean(PreferencesKeys.VIEWPAGER_SMOOTH_SCROLL, _smoothTabs)
                smoothTabs = _smoothTabs
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = _smoothTabs,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.ww),
            description = stringResource(id = R.string.ww_desc),
            iconResource = R.drawable.reorder,
            onNavigate = {
                wordwrap = !wordwrap
                PreferencesData.setBoolean(PreferencesKeys.WORD_WRAP_ENABLED, wordwrap)
                MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                    if (f.value.get()?.fragment is EditorFragment){
                        (f.value.get()?.fragment as EditorFragment).editor?.isWordwrap = wordwrap
                    }
                    
                }
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = wordwrap,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.keepdl),
            description = stringResource(id = R.string.drawer_lock_desc),
            iconResource = R.drawable.lock,
            onNavigate = {
                drawerLock = !drawerLock
                PreferencesData.setBoolean(PreferencesKeys.KEEP_DRAWER_LOCKED, drawerLock)
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = drawerLock,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.diagonal_scroll),
            description = stringResource(id = R.string.diagonal_scroll_desc),
            iconResource = R.drawable.diagonal_scroll,
            onNavigate = {
                diagonalScroll = !diagonalScroll
                PreferencesData.setBoolean(PreferencesKeys.DIAGONAL_SCROLL, diagonalScroll)
                rkUtils.toast(getString(R.string.rr))
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = diagonalScroll,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.cursor_anim),
            description = stringResource(id = R.string.cursor_anim_desc),
            iconResource = R.drawable.animation,
            onNavigate = {
                cursorAnimation = !cursorAnimation
                PreferencesData.setBoolean(
                    PreferencesKeys.CURSOR_ANIMATION_ENABLED,
                    cursorAnimation,
                )
                MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                    if (f.value.get()?.fragment is EditorFragment){
                        (f.value.get()?.fragment as EditorFragment).editor?.isCursorAnimationEnabled = cursorAnimation
                    }
                    
                }
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = cursorAnimation,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.show_line_number),
            description = stringResource(id = R.string.show_line_number),
            iconResource = R.drawable.linenumbers,
            onNavigate = {
                showLineNumber = !showLineNumber
                PreferencesData.setBoolean(PreferencesKeys.CURSOR_ANIMATION_ENABLED, showLineNumber)
                
                MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                    if (f.value.get()?.fragment is EditorFragment){
                        (f.value.get()?.fragment as EditorFragment).editor?.isLineNumberEnabled = showLineNumber
                    }
                    
                }
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = showLineNumber,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.pin_line_number),
            description = stringResource(id = R.string.pin_line_number),
            iconResource = R.drawable.linenumbers,
            onNavigate = {
                pinLineNumber = !pinLineNumber
                PreferencesData.setBoolean(PreferencesKeys.PIN_LINE_NUMBER, pinLineNumber)
                MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                    if (f.value.get()?.fragment is EditorFragment){
                        (f.value.get()?.fragment as EditorFragment).editor?.setPinLineNumber(pinLineNumber)
                    }
                    
                }
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = pinLineNumber,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.show_suggestions),
            description = stringResource(id = R.string.show_suggestions),
            iconResource = R.drawable.baseline_font_download_24,
            onNavigate = {
                showSuggestions = !showSuggestions
                PreferencesData.setBoolean(PreferencesKeys.SHOW_SUGGESTIONS, showSuggestions)
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = showSuggestions,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.extra_keys),
            description = stringResource(id = R.string.extra_keys_desc),
            iconResource = R.drawable.double_arrows,
            onNavigate = {
                showArrowKeys = !showArrowKeys
                PreferencesData.setBoolean(PreferencesKeys.SHOW_ARROW_KEYS, showArrowKeys)
                MainActivity.activityRef.get()?.let { activity ->
                    if (activity.tabViewModel.fragmentFiles.isEmpty()) {
                        return@let
                    }
                    
                    
                    //todo arrow keys

                    val viewpager = activity.binding.viewpager2
                    val layoutParams = viewpager.layoutParams as RelativeLayout.LayoutParams
                    layoutParams.bottomMargin =
                        rkUtils.dpToPx(
                            if (showArrowKeys) {
                                40f
                            } else {
                                0f
                            },
                            activity,
                        )
                    viewpager.setLayoutParams(layoutParams)
                }
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = showArrowKeys,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.auto_save),
            description = stringResource(id = R.string.auto_save_desc),
            iconResource = R.drawable.save,
            onNavigate = {
                autoSave = !autoSave
                PreferencesData.setBoolean(PreferencesKeys.AUTO_SAVE, autoSave)
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = autoSave,
                    onCheckedChange = null,
                )
            },
        )

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

        PreferenceCategory(
            label = stringResource(id = R.string.auto_save_time),
            description = stringResource(id = R.string.auto_save_time_desc),
            iconResource = R.drawable.save,
            onNavigate = { showAutoSaveDialog = true },
        )
        PreferenceCategory(
            label = stringResource(id = R.string.text_size),
            description = stringResource(id = R.string.text_size_desc),
            iconResource = R.drawable.reorder,
            onNavigate = { showTextSizeDialog = true },
        )
        PreferenceCategory(
            label = stringResource(id = R.string.tab_size),
            description = stringResource(id = R.string.tab_size_desc),
            iconResource = R.drawable.double_arrows,
            onNavigate = { showTabSizeDialog = true },
        )

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
                            if (f.value.get()?.fragment is EditorFragment){
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
                        if (f.value.get()?.fragment is EditorFragment){
                            (f.value.get()?.fragment as EditorFragment).editor?.tabWidth = tabSizeValue.toInt()
                        }
                        
                    }
                    showTabSizeDialog = false
                },
                onDismiss = { showTabSizeDialog = false },
            )
        }
        PreferenceCategory(
            label = stringResource(id = R.string.editor_font),
            description = stringResource(id = R.string.editor_font_desc),
            iconResource = R.drawable.baseline_font_download_24,
            onNavigate = {
                editorFont = !editorFont
                PreferencesData.setBoolean(PreferencesKeys.EDITOR_FONT, editorFont)
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = editorFont,
                    onCheckedChange = null,
                )
            },
        )
    }
}
