package com.rk.xededitor.ui.screens.settings.editor

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.file.smoothTabs
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.components.InputDialog
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout

private fun updateEditorSettings() {
    MainActivity.withContext {
        adapter?.tabFragments?.values?.forEach {
            lifecycleScope.launch { (it.get()?.fragment as? EditorFragment)?.editor?.applySettings() }
        }
    }
}

@Composable
private fun EditorSettingsToggle(
    modifier: Modifier = Modifier,
    label: String,
    description: String? = null,
    @DrawableRes iconRes: Int? = null,
    default: Boolean,
    reactiveSideEffect: ((checked: Boolean) -> Boolean)? = null,
    sideEffect: ((checked: Boolean) -> Unit)? = null,
    showSwitch: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    isSwitchLocked: Boolean = false
) {
    SettingsToggle(
        modifier = modifier,
        label = label,
        description = description,
        iconRes = iconRes,
        default = default,
        reactiveSideEffect = reactiveSideEffect,
        showSwitch = showSwitch,
        onLongClick = onLongClick,
        isEnabled = isEnabled,
        isSwitchLocked = isSwitchLocked,
        sideEffect = {
            DefaultScope.launch {
                if (showSwitch){
                    updateEditorSettings()
                }
            }
            sideEffect?.invoke(it)
        },
    )
}

@Composable
fun SettingsEditorScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.editor), backArrowVisible = true) {
        val context = LocalContext.current

        var showAutoSaveDialog by remember { mutableStateOf(false) }
        var showTextSizeDialog by remember { mutableStateOf(false) }
        var showTabSizeDialog by remember { mutableStateOf(false) }
        var autoSaveTimeValue by remember { mutableIntStateOf(Settings.auto_save_interval) }
        var textSizeValue by remember { mutableIntStateOf(Settings.editor_text_size) }
        var tabSizeValue by remember { mutableIntStateOf(Settings.tab_size) }
        var showLineSpacingDialog by remember { mutableStateOf(false) }
        var showLineSpacingMultiplierDialog by remember { mutableStateOf(false) }
        var lineSpacingValue by remember { mutableFloatStateOf(Settings.line_spacing) }



        PreferenceGroup(heading = stringResource(strings.content)) {

            EditorSettingsToggle(label = stringResource(strings.mutators),
                description = stringResource(strings.mutator_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    navController.navigate(SettingsRoutes.ManageMutators.route)
                })

            EditorSettingsToggle(label = stringResource(strings.unrestricted_file),
                description = stringResource(strings.unrestricted_file_desc),
                default = Settings.unrestricted_files,
                sideEffect = {
                    Settings.unrestricted_files = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(strings.restore_sessions),
                description = stringResource(strings.restore_sessions_desc),
                default = Settings.restore_session,
                sideEffect = {
                    Settings.restore_session = it

                    DefaultScope.launch(Dispatchers.Main) {
                        delay(300)
                        if (it){
                            MaterialAlertDialogBuilder(context).apply {
                                setTitle(strings.experimental_feature.getString())
                                setMessage(strings.experimental_session_restore_warning.getString())
                                setPositiveButton(strings.ok,null)
                                show()
                            }
                        }
                    }

                }
            )

           /* EditorSettingsToggle(label = stringResource(strings.scroll_to_bottom),
                description = stringResource(strings.scroll_to_bottom_desc),
                default = false,
                key = PreferencesKeys.SCROLL_TO_BOTTOM,
                sideEffect = {
                    if (it) {
                        toast(strings.ni.getString())
                    }
                }) */


            EditorSettingsToggle(label = stringResource(id = strings.ww),
                description = stringResource(id = strings.ww_desc),
                default = Settings.wordwrap,
                sideEffect = {
                    Settings.wordwrap = it
                }
            )

            EditorSettingsToggle(label = stringResource(strings.txt_ww),
                description = stringResource(strings.txt_ww_desc),
                default =  Settings.word_wrap_for_text,
                sideEffect = {
                    Settings.word_wrap_for_text = it

                    MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                        if (f.value.get()?.fragment is EditorFragment) {
                            (f.value.get()?.fragment as EditorFragment).apply {
                                if (file?.getName()?.endsWith(".txt") == true) {
                                    editor?.isWordwrap = it
                                }
                            }
                        }
                    }
                }
            )
        }




        PreferenceGroup(heading = stringResource(id = strings.editor)) {

            EditorSettingsToggle(
                label = stringResource(strings.soft_keyboard_always),
                description = stringResource(strings.soft_keyboard_always_desc),
                default = Settings.always_show_soft_keyboard,
                sideEffect = {
                    Settings.always_show_soft_keyboard = it
                }
            )

            EditorSettingsToggle(label = stringResource(id = strings.line_spacing),
                description = stringResource(id = strings.line_spacing),
                showSwitch = false,
                default = false,
                sideEffect = {
                    showLineSpacingDialog = true
                })


            EditorSettingsToggle(label = stringResource(id = strings.cursor_anim),
                description = stringResource(id = strings.cursor_anim_desc),
                default = Settings.cursor_animation,
                sideEffect = {
                    Settings.cursor_animation = it
                }
            )
            EditorSettingsToggle(label = stringResource(id = strings.show_line_number),
                description = stringResource(id = strings.show_line_number),
                default = Settings.show_line_numbers,
                sideEffect = {
                    Settings.show_line_numbers = it
                }
            )
            EditorSettingsToggle(label = stringResource(id = strings.show_suggestions),
                description = stringResource(id = strings.show_suggestions),
                default = Settings.show_suggestions,
                sideEffect = {
                    Settings.show_suggestions = it
                })
            EditorSettingsToggle(label = stringResource(id = strings.pin_line_number),
                description = stringResource(id = strings.pin_line_number),
                default = Settings.pin_line_number,
                sideEffect = {
                    Settings.pin_line_number = it
                }
            )

            EditorSettingsToggle(label = stringResource(strings.manage_editor_font),
                description = stringResource(strings.manage_editor_font),
                showSwitch = false,
                default = false,
                sideEffect = {
                    navController.navigate(SettingsRoutes.EditorFontScreen.route)
                })

            EditorSettingsToggle(label = stringResource(id = strings.text_size),
                description = stringResource(id = strings.text_size_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    showTextSizeDialog = true
                })

        }



        PreferenceGroup(heading = stringResource(strings.other)) {
            EditorSettingsToggle(label = stringResource(id = strings.extra_keys),
                description = stringResource(id = strings.extra_keys_desc),
                default = Settings.show_arrow_keys,
                sideEffect = {
                    Settings.show_arrow_keys = it

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

            EditorSettingsToggle(label = stringResource(strings.default_encoding),
                description = stringResource(strings.default_encoding_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    navController.navigate(SettingsRoutes.DefaultEncoding.route)
                })

            EditorSettingsToggle(label = stringResource(id = strings.smooth_tabs),
                description = stringResource(id = strings.smooth_tab_desc),
                default = Settings.smooth_tabs,
                sideEffect = {
                    Settings.smooth_tabs = it
                    smoothTabs = it
                })
            EditorSettingsToggle(
                label = stringResource(id = strings.keepdl),
                description = stringResource(id = strings.drawer_lock_desc),
                default = Settings.keep_drawer_locked,
                sideEffect = {
                    Settings.keep_drawer_locked = it
                }
            )
            EditorSettingsToggle(
                label = stringResource(id = strings.auto_save),
                description = stringResource(id = strings.auto_save_desc),
                default = Settings.auto_save,
                sideEffect = {
                    Settings.auto_save = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(strings.sora_s),
                description = stringResource(strings.sora_s_desc),
                default = Settings.use_sora_search,
                sideEffect = {
                    Settings.use_sora_search = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.auto_save_time),
                description = stringResource(id = strings.auto_save_time_desc),
                sideEffect = {
                    showAutoSaveDialog = true
                },
                default = false,
                showSwitch = false,
            )

            EditorSettingsToggle(label = stringResource(id = strings.tab_size),
                description = stringResource(id = strings.tab_size_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    showTabSizeDialog = true
                })
        }

        if (showLineSpacingDialog) {
            InputDialog(
                title = stringResource(id = strings.line_spacing),
                inputLabel = stringResource(id = strings.line_spacing),
                inputValue = lineSpacingValue.toString(),
                onInputValueChange = {
                    it.toFloatOrNull()?.let { float -> lineSpacingValue = float } ?: run {
                        lineSpacingValue = Settings.line_spacing
                    }
                },
                onConfirm = {
                    if (lineSpacingValue.toInt() < 0) {
                        toast(context.getString(strings.v_small))
                        lineSpacingValue = Settings.line_spacing
                    } else {
                        Settings.line_spacing = lineSpacingValue

                        MainActivity.activityRef.get()?.adapter?.tabFragments?.values?.forEach {
                            if (it.get()?.fragment is EditorFragment) {
                                (it.get()?.fragment as EditorFragment).editor?.lineSpacingExtra =
                                    lineSpacingValue.toFloat()
                            }
                        }
                        showLineSpacingDialog = false
                    }

                },
                onDismiss = { showLineSpacingDialog = false },
            )
        }

        if (showAutoSaveDialog) {
            InputDialog(
                title = stringResource(id = strings.auto_save_time),
                inputLabel = stringResource(id = strings.intervalinMs),
                inputValue = autoSaveTimeValue.toString(),
                onInputValueChange = { it.toIntOrNull()?.let { int -> autoSaveTimeValue = int } ?: run{
                    autoSaveTimeValue = Settings.auto_save_interval
                } },
                onConfirm = {
                    if (autoSaveTimeValue.toInt() < 3000) {
                        toast(context.getString(strings.v_small))
                        autoSaveTimeValue = Settings.auto_save_interval
                    } else {
                        Settings.auto_save_interval = autoSaveTimeValue
                        showAutoSaveDialog = false
                    }

                },
                onDismiss = { showAutoSaveDialog = false },
            )
        }

        if (showTextSizeDialog) {
            InputDialog(
                title = stringResource(id = strings.text_size),
                inputLabel = stringResource(id = strings.text_size),
                inputValue = textSizeValue.toString(),
                onInputValueChange = { it.toIntOrNull()?.let { int -> textSizeValue = int }  ?: run{
                    textSizeValue = Settings.editor_text_size
                }},
                onConfirm = {
                   if (textSizeValue.toInt() > 32) {
                        toast(context.getString(strings.v_large))
                        textSizeValue = Settings.editor_text_size
                    } else if (textSizeValue.toInt() < 8) {
                        toast(context.getString(strings.v_small))
                        textSizeValue = Settings.editor_text_size
                    } else {
                        Settings.editor_text_size = textSizeValue

                        MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                            if (f.value.get()?.fragment is EditorFragment) {
                                (f.value.get()?.fragment as EditorFragment).editor?.setTextSize(
                                    textSizeValue.toFloat()
                                )
                            }

                        }
                        showTextSizeDialog = false
                    }

                },
                onDismiss = { showTextSizeDialog = false },
            )
        }
        if (showTabSizeDialog) {
            InputDialog(
                title = stringResource(id = strings.tab_size),
                inputLabel = stringResource(id = strings.tab_size),
                inputValue = tabSizeValue.toString(),
                onInputValueChange = { it.toIntOrNull()?.let { int -> tabSizeValue = int } ?: run{
                    tabSizeValue = Settings.tab_size
                } },
                onConfirm = {
                    if (tabSizeValue.toInt() > 16) {
                        toast(context.getString(strings.v_large))
                        tabSizeValue = Settings.tab_size
                    } else if (tabSizeValue.toInt() < 1) {
                        toast(context.getString(strings.v_small))
                        tabSizeValue = Settings.tab_size
                    } else {
                        Settings.tab_size = tabSizeValue

                        MainActivity.activityRef.get()?.adapter?.tabFragments?.forEach { f ->
                            if (f.value.get()?.fragment is EditorFragment) {
                                (f.value.get()?.fragment as EditorFragment).editor?.tabWidth =
                                    tabSizeValue.toInt()
                            }

                        }
                        showTabSizeDialog = false
                    }
                },
                onDismiss = { showTabSizeDialog = false },
            )
        }

    }
}