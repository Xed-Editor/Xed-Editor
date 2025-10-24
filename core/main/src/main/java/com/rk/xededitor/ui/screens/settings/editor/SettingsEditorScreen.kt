package com.rk.xededitor.ui.screens.settings.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.EditorTab
import com.rk.xededitor.ui.activities.main.MainActivity
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.components.EditorSettingsToggle
import com.rk.xededitor.ui.components.SingleInputDialog
import com.rk.xededitor.ui.components.NextScreenCard
import com.rk.xededitor.ui.components.SettingsToggle
import com.rk.xededitor.ui.screens.settings.app.InbuiltFeatures
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

@Composable
fun SettingsEditorScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.editor), backArrowVisible = true) {
        val context = LocalContext.current

        var showTextSizeDialog by remember { mutableStateOf(false) }
        var textSizeValue by remember { mutableStateOf(Settings.editor_text_size.toString()) }
        var textSizeError by remember { mutableStateOf<String?>(null) }

        var showTabSizeDialog by remember { mutableStateOf(false) }
        var tabSizeValue by remember { mutableStateOf(Settings.tab_size.toString()) }
        var tabSizeError by remember { mutableStateOf<String?>(null) }

        var showLineSpacingDialog by remember { mutableStateOf(false) }
        var lineSpacingValue by remember { mutableStateOf(Settings.line_spacing.toString()) }
        var lineSpacingError by remember { mutableStateOf<String?>(null) }

        PreferenceGroup {
            NextScreenCard(
                navController = navController,
                label = stringResource(strings.lsp_settings),
                description = stringResource(strings.lsp_settings_desc),
                route = SettingsRoutes.LspSettings
            )
        }

        PreferenceGroup(heading = stringResource(strings.content)) {
            if (InbuiltFeatures.mutators.state.value) {
                NextScreenCard(
                    label = stringResource(strings.mutators),
                    description = stringResource(strings.mutator_desc),
                    route = SettingsRoutes.ManageMutators
                )
            }

            EditorSettingsToggle(
                label = stringResource(id = strings.word_wrap),
                description = stringResource(id = strings.word_wrap_desc),
                default = Settings.word_wrap,
                sideEffect = {
                    Settings.word_wrap = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(strings.read_mode),
                description = stringResource(strings.read_mode_desc),
                default = Settings.read_only_default,
                sideEffect = {
                    Settings.read_only_default = it
                }
            )
        }

        PreferenceGroup(heading = stringResource(id = strings.editor)) {
            EditorSettingsToggle(
                label = stringResource(strings.disable_virtual_kbd),
                description = stringResource(strings.disable_virtual_kbd_desc),
                default = Settings.hide_soft_keyboard_if_hardware,
                sideEffect = {
                    Settings.hide_soft_keyboard_if_hardware = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.line_spacing),
                description = stringResource(id = strings.line_spacing_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    showLineSpacingDialog = true
                }
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.cursor_anim),
                description = stringResource(id = strings.cursor_anim_desc),
                default = Settings.cursor_animation,
                sideEffect = {
                    Settings.cursor_animation = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.show_line_number),
                description = stringResource(id = strings.show_line_number),
                default = Settings.show_line_numbers,
                sideEffect = {
                    Settings.show_line_numbers = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.pin_line_number),
                description = stringResource(id = strings.pin_line_number),
                default = Settings.pin_line_number,
                sideEffect = {
                    Settings.pin_line_number = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.render_whitespace),
                description = stringResource(id = strings.render_whitespace_desc),
                default = Settings.render_whitespace,
                sideEffect = {
                    Settings.render_whitespace = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.show_suggestions),
                description = stringResource(id = strings.show_suggestions),
                default = Settings.show_suggestions,
                sideEffect = {
                    Settings.show_suggestions = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.enable_sticky_scroll),
                description = stringResource(id = strings.enable_sticky_scroll_desc),
                default = Settings.sticky_scroll,
                sideEffect = {
                    Settings.sticky_scroll = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.enable_quick_deletion),
                description = stringResource(id = strings.enable_quick_deletion_desc),
                default = Settings.quick_deletion,
                sideEffect = {
                    Settings.quick_deletion = it
                }
            )

            NextScreenCard(
                label = stringResource(strings.manage_editor_font),
                description = stringResource(strings.manage_editor_font),
                route = SettingsRoutes.EditorFontScreen
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.text_size),
                description = stringResource(id = strings.text_size_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    showTextSizeDialog = true
                }
            )

            SettingsToggle(
                label = stringResource(strings.text_mate_suggestion),
                description = stringResource(strings.text_mate_suggestion_desc),
                default = Settings.textmate_suggestion,
                sideEffect = {
                    Settings.textmate_suggestion = it
                    toast(strings.restart_required)
                }
            )
        }

        PreferenceGroup(heading = stringResource(strings.other)) {
            EditorSettingsToggle(
                label = stringResource(id = strings.restore_sessions),
                description = stringResource(id = strings.restore_sessions_desc),
                default = Settings.restore_sessions,
                sideEffect = {
                    Settings.restore_sessions = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.extra_keys),
                description = stringResource(id = strings.extra_keys_desc),
                default = Settings.show_arrow_keys,
                sideEffect = {
                    Settings.show_arrow_keys = it
                }
            )

            NextScreenCard(
                label = stringResource(strings.default_encoding),
                description = stringResource(strings.default_encoding_desc),
                route = SettingsRoutes.DefaultEncoding
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.keep_drawer_locked),
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
                label = stringResource(id = strings.smooth_tabs),
                description = stringResource(id = strings.smooth_tab_desc),
                default = Settings.smooth_tabs,
                sideEffect = {
                    Settings.smooth_tabs = it
                }
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.tab_size),
                description = stringResource(id = strings.tab_size_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    showTabSizeDialog = true
                }
            )

            EditorSettingsToggle(
                label = stringResource(strings.use_tabs),
                description = stringResource(strings.use_tabs_desc),
                default = Settings.actual_tabs,
                sideEffect = {
                    Settings.actual_tabs = it

                    MainActivity.instance?.apply {
                        viewModel.tabs.forEach { tab ->
                            if (tab is EditorTab) {
                                (tab.editorState.editor?.editorLanguage as? TextMateLanguage)?.useTab(
                                    it
                                )
                            }
                        }
                    }
                }
            )
        }

        if (showLineSpacingDialog) {
            SingleInputDialog(
                title = stringResource(id = strings.line_spacing),
                inputLabel = stringResource(id = strings.line_spacing),
                inputValue = lineSpacingValue,
                errorMessage = lineSpacingError,
                onInputValueChange = {
                    lineSpacingValue = it
                    lineSpacingError = null
                    if (lineSpacingValue.toFloatOrNull() == null) {
                        lineSpacingError = context.getString(strings.value_invalid)
                    } else if (lineSpacingValue.toFloat() < 0.6f) {
                        lineSpacingError = context.getString(strings.value_small)
                    }
                },
                onConfirm = {
                    Settings.line_spacing = lineSpacingValue.toFloat()
                    reapplyEditorSettings()
                },
                onFinish = {
                    lineSpacingValue = Settings.line_spacing.toString()
                    lineSpacingError = null
                    showLineSpacingDialog = false
                },
            )
        }

        if (showTextSizeDialog) {
            SingleInputDialog(
                title = stringResource(id = strings.text_size),
                inputLabel = stringResource(id = strings.text_size),
                inputValue = textSizeValue,
                errorMessage = textSizeError,
                onInputValueChange = {
                    textSizeValue = it
                    textSizeError = null
                    if (it.toIntOrNull() == null) {
                        textSizeError = context.getString(strings.value_invalid)
                    } else if (it.toInt() > 100) {
                        textSizeError = context.getString(strings.value_large)
                    } else if (it.toInt() < 6) {
                        textSizeError = context.getString(strings.value_small)
                    }
                },
                onConfirm = {
                    Settings.editor_text_size = textSizeValue.toInt()
                    reapplyEditorSettings()
                },
                onFinish = {
                    textSizeValue = Settings.editor_text_size.toString()
                    textSizeError = null
                    showTextSizeDialog = false
                },
            )
        }

        if (showTabSizeDialog) {
            SingleInputDialog(
                title = stringResource(id = strings.tab_size),
                inputLabel = stringResource(id = strings.tab_size),
                inputValue = tabSizeValue,
                errorMessage = tabSizeError,
                onInputValueChange = {
                    tabSizeValue = it
                    tabSizeError = null
                    if (tabSizeValue.toIntOrNull() == null) {
                        tabSizeError = context.getString(strings.value_invalid)
                    } else if (tabSizeValue.toInt() > 16) {
                        tabSizeError = context.getString(strings.value_large)
                    } else if (tabSizeValue.toInt() < 1) {
                        tabSizeError = context.getString(strings.value_small)
                    }
                },
                onConfirm = {
                    Settings.tab_size = tabSizeValue.toInt()
                    reapplyEditorSettings()
                },
                onFinish = {
                    tabSizeValue = Settings.tab_size.toString()
                    tabSizeError = null
                    showTabSizeDialog = false
                },
            )
        }
    }
}

private fun reapplyEditorSettings() {
    MainActivity.instance?.apply {
        viewModel.tabs.forEach {
            if (it is EditorTab) {
                it.editorState.editor?.applySettings()
            }
        }
    }
}