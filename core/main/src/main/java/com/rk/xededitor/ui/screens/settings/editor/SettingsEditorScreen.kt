package com.rk.xededitor.ui.screens.settings.editor

import android.view.View
import android.widget.HorizontalScrollView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.DefaultScope
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.EditorTab
import com.rk.xededitor.ui.activities.main.MainActivity
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.components.EditorSettingsToggle
import com.rk.xededitor.ui.components.InputDialog
import com.rk.xededitor.ui.components.NextScreenCard
import com.rk.xededitor.ui.components.SettingsToggle
import com.rk.xededitor.ui.screens.settings.app.InbuiltFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.sequences.filterIsInstance

@Composable
fun SettingsEditorScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.editor), backArrowVisible = true) {
        val context = LocalContext.current

        var showTextSizeDialog by remember { mutableStateOf(false) }
        var showTabSizeDialog by remember { mutableStateOf(false) }
        var textSizeValue by remember { mutableStateOf(Settings.editor_text_size.toString()) }
        var tabSizeValue by remember { mutableStateOf(Settings.tab_size.toString()) }
        var showLineSpacingDialog by remember { mutableStateOf(false) }
        var lineSpacingValue by remember { mutableStateOf(Settings.line_spacing.toString()) }



        PreferenceGroup(heading = stringResource(strings.content)) {
            if (InbuiltFeatures.mutators.state.value) {
                NextScreenCard(
                    label = stringResource(strings.mutators),
                    description = stringResource(strings.mutator_desc),
                    route = SettingsRoutes.ManageMutators
                )
            }

            EditorSettingsToggle(label = stringResource(id = strings.ww),
                description = stringResource(id = strings.ww_desc),
                default = Settings.wordwrap,
                sideEffect = {
                    Settings.wordwrap = it
                }
            )

            EditorSettingsToggle(label = stringResource(strings.txt_ww),
                description = stringResource(strings.txt_ww_desc),
                default = Settings.word_wrap_for_text,
                sideEffect = {
                    Settings.word_wrap_for_text = it

                }
            )

            EditorSettingsToggle(label = stringResource(strings.read_mode),
                description = stringResource(strings.read_mode_desc),
                default = Settings.readOnlyByDefault,
                sideEffect = {
                    Settings.readOnlyByDefault = it
                }
            )
        }




        PreferenceGroup(heading = stringResource(id = strings.editor)) {

            EditorSettingsToggle(label = stringResource(id = strings.restore_sessions),
                description = stringResource(id = strings.restore_sessions_desc),
                default = Settings.restore_sessions,
                sideEffect = {
                    Settings.restore_sessions = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(strings.isDisableSoftKbdIfHardKbdAvailable),
                description = stringResource(strings.isDisableSoftKbdIfHardKbdAvailable_desc),
                default = Settings.hide_soft_keyboard_if_hardware,
                sideEffect = {
                    Settings.hide_soft_keyboard_if_hardware = it
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

            NextScreenCard(
                label = stringResource(strings.manage_editor_font),
                description = stringResource(strings.manage_editor_font),
                route = SettingsRoutes.EditorFontScreen
            )

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
                })

            NextScreenCard(
                label = stringResource(strings.default_encoding),
                description = stringResource(strings.default_encoding_desc),
                route = SettingsRoutes.DefaultEncoding
            )

            EditorSettingsToggle(label = stringResource(id = strings.smooth_tabs),
                description = stringResource(id = strings.smooth_tab_desc),
                default = Settings.smooth_tabs,
                sideEffect = {
                    Settings.smooth_tabs = it
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
                    lineSpacingValue = it
                },
                onConfirm = {
                    if (lineSpacingValue.toFloatOrNull() == null) {
                        toast(strings.inavalid_v)
                        lineSpacingValue = Settings.line_spacing.toString()
                    } else if (lineSpacingValue.toFloat() < 0) {
                        toast(context.getString(strings.v_small))
                        lineSpacingValue = Settings.line_spacing.toString()
                    } else {
                        Settings.line_spacing = lineSpacingValue.toFloat()

                        showLineSpacingDialog = false
                    }

                },
                onDismiss = { showLineSpacingDialog = false },
            )
        }

        if (showTextSizeDialog) {
            InputDialog(
                title = stringResource(id = strings.text_size),
                inputLabel = stringResource(id = strings.text_size),
                inputValue = textSizeValue.toString(),
                onInputValueChange = {
                    textSizeValue = it
                },
                onConfirm = {
                    if (textSizeValue.toIntOrNull() == null) {
                        toast(strings.inavalid_v)
                        textSizeValue = Settings.editor_text_size.toString()
                    } else if (textSizeValue.toInt() > 32) {
                        toast(context.getString(strings.v_large))
                        textSizeValue = Settings.editor_text_size.toString()
                    } else if (textSizeValue.toInt() < 8) {
                        toast(context.getString(strings.v_small))
                        textSizeValue = Settings.editor_text_size.toString()
                    } else {
                        Settings.editor_text_size = textSizeValue.toInt()

                        MainActivity.instance?.apply {
                            viewModel.tabs.forEach {
                                if (it is EditorTab) {
                                    it.editorState.editor?.applySettings()
                                }
                            }
                        }
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
                onInputValueChange = {
                    tabSizeValue = it
                },
                onConfirm = {
                    if (tabSizeValue.toIntOrNull() == null) {
                        toast(strings.inavalid_v)
                        tabSizeValue = Settings.tab_size.toString()
                    } else if (tabSizeValue.toInt() > 16) {
                        toast(context.getString(strings.v_large))
                        tabSizeValue = Settings.tab_size.toString()
                    } else if (tabSizeValue.toInt() < 1) {
                        toast(context.getString(strings.v_small))
                        tabSizeValue = Settings.tab_size.toString()
                    } else {
                        Settings.tab_size = tabSizeValue.toInt()

                        MainActivity.instance?.apply {
                            viewModel.tabs.forEach {
                                if (it is EditorTab) {
                                    it.editorState.editor?.applySettings()
                                }
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