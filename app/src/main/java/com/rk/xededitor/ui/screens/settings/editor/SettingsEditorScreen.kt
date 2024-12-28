package com.rk.xededitor.ui.screens.settings.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.rk.resources.strings
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.smoothTabs
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.components.InputDialog
import com.rk.xededitor.ui.components.SettingsToggle
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout


@Composable
fun SettingsEditorScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.editor), backArrowVisible = true) {
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

        var showLineSpacingDialog by remember { mutableStateOf(false) }
        var showLineSpacingMultiplierDialog by remember { mutableStateOf(false) }

        var lineSpacingValue by remember {
            mutableStateOf(PreferencesData.getString(PreferencesKeys.LINE_SPACING, "0"))
        }

        var lineSpacingMultiplierValue by remember {
            mutableStateOf(PreferencesData.getString(PreferencesKeys.LINE_SPACING_MULTIPLAYER, "1"))
        }


        PreferenceGroup(heading = stringResource(strings.content)) {

            SettingsToggle(label = "Mutators",
                description = "Create micro tools",
                showSwitch = false,
                sideEffect = {
                    navController.navigate(SettingsRoutes.ManageMutators.route)
                })


            SettingsToggle(label = stringResource(id = strings.ww),
                description = stringResource(id = strings.ww_desc),
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
                key = PreferencesKeys.WORD_WRAP_TXT,
                default = true,
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
        }




        PreferenceGroup(heading = stringResource(id = strings.editor)) {


            SettingsToggle(label = stringResource(id = strings.line_spacing),
                description = stringResource(id = strings.line_spacing),
                showSwitch = false,
                sideEffect = {
                    showLineSpacingDialog = true
                })


            SettingsToggle(label = stringResource(id = strings.line_spacing_multiplier),
                description = stringResource(id = strings.line_spacing_multiplier),
                showSwitch = false,
                sideEffect = {
                    showLineSpacingMultiplierDialog = true
                })


            SettingsToggle(label = stringResource(id = strings.cursor_anim),
                description = stringResource(id = strings.cursor_anim_desc),
                key = PreferencesKeys.CURSOR_ANIMATION_ENABLED,
                default = false,
                sideEffect = {
                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).editor?.isCursorAnimationEnabled =
                                it
                        }
                    }
                })
            SettingsToggle(label = stringResource(id = strings.show_line_number),
                description = stringResource(id = strings.show_line_number),
                key = PreferencesKeys.SHOW_LINE_NUMBERS,
                default = true,
                sideEffect = {
                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).editor?.isLineNumberEnabled =
                                it
                        }
                    }
                })
            SettingsToggle(label = stringResource(id = strings.show_suggestions),
                description = stringResource(id = strings.show_suggestions),
                key = PreferencesKeys.SHOW_SUGGESTIONS,
                default = false,
                sideEffect = {
                    MainActivity.activityRef.get()?.adapter?.tabFragments?.values?.forEach { f ->
                        if (f.get() != null && f.get()?.fragment is EditorFragment) {
                            (f.get()?.fragment as EditorFragment).editor?.showSuggestions(it)
                        }
                    }
                })
            SettingsToggle(label = stringResource(id = strings.pin_line_number),
                description = stringResource(id = strings.pin_line_number),
                key = PreferencesKeys.PIN_LINE_NUMBER,
                default = false,
                sideEffect = {
                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).editor?.setPinLineNumber(it)
                        }
                    }
                })

            SettingsToggle(label = stringResource(strings.manage_editor_font),
                description = stringResource(strings.manage_editor_font),
                showSwitch = false,
                default = false,
                sideEffect = {
                    navController.navigate(SettingsRoutes.EditorFontScreen.route)
                })

            SettingsToggle(label = stringResource(id = strings.text_size),
                description = stringResource(id = strings.text_size_desc),
                showSwitch = false,
                sideEffect = {
                    showTextSizeDialog = true
                })

        }



        PreferenceGroup(heading = stringResource(strings.other)) {
            SettingsToggle(label = stringResource(id = strings.extra_keys),
                description = stringResource(id = strings.extra_keys_desc),
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

            SettingsToggle(label = stringResource(strings.default_encoding),
                description = stringResource(strings.default_encoding_desc),
                showSwitch = false,
                sideEffect = {
                    navController.navigate(SettingsRoutes.DefaultEncoding.route)
                })

            SettingsToggle(label = stringResource(id = strings.smooth_tabs),
                description = stringResource(id = strings.smooth_tab_desc),
                key = PreferencesKeys.VIEWPAGER_SMOOTH_SCROLL,
                default = false,
                sideEffect = {
                    smoothTabs = it
                })
            SettingsToggle(
                label = stringResource(id = strings.keepdl),
                description = stringResource(id = strings.drawer_lock_desc),
                key = PreferencesKeys.KEEP_DRAWER_LOCKED,
                default = false,
            )
            SettingsToggle(
                label = stringResource(id = strings.auto_save),
                description = stringResource(id = strings.auto_save_desc),
                key = PreferencesKeys.AUTO_SAVE,
                default = false,
            )

            SettingsToggle(
                label = stringResource(strings.sora_s),
                description = stringResource(strings.sora_s_desc),
                key = PreferencesKeys.USE_SORA_SEARCH,
                default = false,
            )

            SettingsToggle(
                label = stringResource(id = strings.auto_save_time),
                description = stringResource(id = strings.auto_save_time_desc),
                sideEffect = {
                    showAutoSaveDialog = true
                },
                showSwitch = false,
            )

            SettingsToggle(label = stringResource(id = strings.tab_size),
                description = stringResource(id = strings.tab_size_desc),
                showSwitch = false,
                sideEffect = {
                    showTabSizeDialog = true
                })
        }

        if (showLineSpacingDialog) {
            InputDialog(
                title = stringResource(id = strings.line_spacing),
                inputLabel = stringResource(id = strings.line_spacing),
                inputValue = lineSpacingValue,
                onInputValueChange = {
                    lineSpacingValue = it
                },
                onConfirm = {
                    if (lineSpacingValue.any { !it.isDigit() }) {
                        rkUtils.toast(context.getString(strings.inavalid_v))
                        lineSpacingValue =
                            PreferencesData.getString(PreferencesKeys.LINE_SPACING, "0")
                    } else if (lineSpacingValue.toInt() < 0) {
                        rkUtils.toast(context.getString(strings.v_small))
                        lineSpacingValue =
                            PreferencesData.getString(PreferencesKeys.LINE_SPACING, "0")
                    } else {
                        PreferencesData.setString(
                            PreferencesKeys.LINE_SPACING,
                            lineSpacingValue,
                        )
                        MainActivity.activityRef.get()?.adapter?.tabFragments?.values?.forEach {
                            if (it.get()?.fragment is EditorFragment){
                                (it.get()?.fragment as EditorFragment)?.editor?.lineSpacingExtra = lineSpacingValue.toFloat()
                            }
                        }

                    }
                    showLineSpacingDialog = false
                },
                onDismiss = { showLineSpacingDialog = false },
            )
        }

        if (showLineSpacingMultiplierDialog) {
            InputDialog(
                title = stringResource(id = strings.line_spacing_multiplier),
                inputLabel = stringResource(id = strings.line_spacing_multiplier),
                inputValue = lineSpacingMultiplierValue,
                onInputValueChange = {
                    lineSpacingMultiplierValue = it
                },
                onConfirm = {
                    if (lineSpacingMultiplierValue.any { !it.isDigit() }) {
                        rkUtils.toast(context.getString(strings.inavalid_v))
                        lineSpacingMultiplierValue =
                            PreferencesData.getString(PreferencesKeys.LINE_SPACING_MULTIPLAYER, "1")
                    } else if (lineSpacingValue.toInt() < 0) {
                        rkUtils.toast(context.getString(strings.v_small))
                        lineSpacingMultiplierValue =
                            PreferencesData.getString(PreferencesKeys.LINE_SPACING_MULTIPLAYER, "1")
                    } else {
                        PreferencesData.setString(
                            PreferencesKeys.LINE_SPACING_MULTIPLAYER,
                            lineSpacingMultiplierValue,
                        )
                        MainActivity.activityRef.get()?.adapter?.tabFragments?.values?.forEach {
                            if (it.get()?.fragment is EditorFragment){
                                (it.get()?.fragment as EditorFragment)?.editor?.lineSpacingMultiplier = lineSpacingMultiplierValue.toFloat()
                            }
                        }
                    }
                    showLineSpacingMultiplierDialog = false
                },
                onDismiss = { showLineSpacingMultiplierDialog = false },
            )
        }

        if (showAutoSaveDialog) {
            InputDialog(
                title = stringResource(id = strings.auto_save_time),
                inputLabel = stringResource(id = strings.intervalinMs),
                inputValue = autoSaveTimeValue,
                onInputValueChange = { autoSaveTimeValue = it },
                onConfirm = {
                    if (autoSaveTimeValue.any { !it.isDigit() }) {
                        rkUtils.toast(context.getString(strings.inavalid_v))
                        autoSaveTimeValue =
                            PreferencesData.getString(PreferencesKeys.AUTO_SAVE_TIME_VALUE, "10000")
                    } else if (autoSaveTimeValue.toInt() < 1000) {
                        rkUtils.toast(context.getString(strings.v_small))
                        autoSaveTimeValue =
                            PreferencesData.getString(PreferencesKeys.AUTO_SAVE_TIME_VALUE, "10000")
                    } else {
                        PreferencesData.setString(
                            PreferencesKeys.AUTO_SAVE_TIME_VALUE,
                            autoSaveTimeValue,
                        )
                    }
                    showAutoSaveDialog = false
                },
                onDismiss = { showAutoSaveDialog = false },
            )
        }

        if (showTextSizeDialog) {
            InputDialog(
                title = stringResource(id = strings.text_size),
                inputLabel = stringResource(id = strings.text_size),
                inputValue = textSizeValue,
                onInputValueChange = { textSizeValue = it },
                onConfirm = {
                    if (textSizeValue.any { !it.isDigit() }) {
                        rkUtils.toast(context.getString(strings.inavalid_v))
                        textSizeValue = PreferencesData.getString(PreferencesKeys.TEXT_SIZE, "14")
                    } else if (textSizeValue.toInt() > 32) {
                        rkUtils.toast(context.getString(strings.v_large))
                        textSizeValue = PreferencesData.getString(PreferencesKeys.TEXT_SIZE, "14")
                    } else if (textSizeValue.toInt() < 8) {
                        rkUtils.toast(context.getString(strings.v_small))
                        textSizeValue = PreferencesData.getString(PreferencesKeys.TEXT_SIZE, "14")
                    } else {
                        PreferencesData.setString(PreferencesKeys.TEXT_SIZE, textSizeValue)
                        MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                            if (f.value.get()?.fragment is EditorFragment) {
                                (f.value.get()?.fragment as EditorFragment).editor?.setTextSize(
                                    textSizeValue.toFloat()
                                )
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
                title = stringResource(id = strings.tab_size),
                inputLabel = stringResource(id = strings.tab_size),
                inputValue = tabSizeValue,
                onInputValueChange = { tabSizeValue = it },
                onConfirm = {
                    if (tabSizeValue.any { !it.isDigit() }) {
                        rkUtils.toast(context.getString(strings.inavalid_v))
                    } else if (tabSizeValue.toInt() > 16) {
                        rkUtils.toast(context.getString(strings.v_large))
                    }
                    PreferencesData.setString(PreferencesKeys.TAB_SIZE, tabSizeValue)

                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).editor?.tabWidth =
                                tabSizeValue.toInt()
                        }

                    }
                    showTabSizeDialog = false
                },
                onDismiss = { showTabSizeDialog = false },
            )
        }

    }
}