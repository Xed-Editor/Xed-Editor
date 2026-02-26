package com.rk.settings.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.rk.activities.main.MainActivity
import com.rk.activities.main.fileTreeViewModel
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.EditorSettingsToggle
import com.rk.components.NextScreenCard
import com.rk.components.SettingsToggle
import com.rk.components.SingleInputDialog
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.editor.KeywordManager
import com.rk.filetree.SortMode
import com.rk.resources.strings
import com.rk.settings.ReactiveSettings
import com.rk.settings.Settings
import com.rk.settings.app.InbuiltFeatures
import com.rk.tabs.editor.EditorTab
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import kotlin.random.Random.Default.nextInt
import kotlinx.coroutines.launch

@Composable
fun SettingsEditorScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.editor), backArrowVisible = true) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var showTextSizeDialog by remember { mutableStateOf(false) }
        var textSizeValue by remember { mutableStateOf(Settings.editor_text_size.toString()) }
        var textSizeError by remember { mutableStateOf<String?>(null) }

        var showTabSizeDialog by remember { mutableStateOf(false) }
        var tabSizeValue by remember { mutableStateOf(Settings.tab_size.toString()) }
        var tabSizeError by remember { mutableStateOf<String?>(null) }

        var showLineSpacingDialog by remember { mutableStateOf(false) }
        var lineSpacingValue by remember { mutableStateOf(Settings.line_spacing.toString()) }
        var lineSpacingError by remember { mutableStateOf<String?>(null) }

        var showAutoSaveDialog by remember { mutableStateOf(false) }
        var autoSaveDelayValue by remember { mutableStateOf(Settings.auto_save_delay.toString()) }
        var autoSaveDelayError by remember { mutableStateOf<String?>(null) }

        var showSortingModeDialog by remember { mutableStateOf(false) }
        var sortingModeValue by remember { mutableIntStateOf(Settings.sort_mode) }

        if (InbuiltFeatures.terminal.state.value) {
            PreferenceGroup(heading = stringResource(strings.language_server)) {
                NextScreenCard(
                    navController = navController,
                    label = stringResource(strings.manage_language_servers),
                    description = stringResource(strings.manage_language_servers_desc),
                    route = SettingsRoutes.LspSettings,
                )

                EditorSettingsToggle(
                    label = stringResource(strings.format_on_save),
                    description = stringResource(strings.format_on_save_desc),
                    default = Settings.format_on_save,
                    sideEffect = { Settings.format_on_save = it },
                )

                EditorSettingsToggle(
                    label = stringResource(strings.insert_final_newline),
                    description = stringResource(strings.insert_final_newline_desc),
                    default = Settings.insert_final_newline,
                    sideEffect = { Settings.insert_final_newline = it },
                )

                EditorSettingsToggle(
                    label = stringResource(strings.trim_trailing_whitespace),
                    description = stringResource(strings.trim_trailing_whitespace_desc),
                    default = Settings.trim_trailing_whitespace,
                    sideEffect = { Settings.trim_trailing_whitespace = it },
                )
            }
        }

        PreferenceGroup(heading = stringResource(strings.intelligent_features)) {
            EditorSettingsToggle(
                label = stringResource(strings.auto_close_tags),
                description = stringResource(strings.auto_close_tags_desc),
                default = Settings.auto_close_tags,
                sideEffect = {
                    Settings.auto_close_tags = it
                    refreshEditors()
                },
            )

            EditorSettingsToggle(
                label = stringResource(strings.bullet_continuation),
                description = stringResource(strings.bullet_continuation_desc),
                default = Settings.bullet_continuation,
                sideEffect = {
                    Settings.bullet_continuation = it
                    refreshEditors()
                },
            )
        }

        PreferenceGroup(heading = stringResource(strings.content)) {
            val wordWrap = remember { mutableStateOf(Settings.word_wrap) }
            val wordWrapTxt = remember { mutableStateOf(Settings.word_wrap_text || Settings.word_wrap) }

            EditorSettingsToggle(
                label = stringResource(id = strings.word_wrap),
                description = stringResource(id = strings.word_wrap_desc),
                state = wordWrap,
                sideEffect = {
                    wordWrap.value = it
                    if (it) {
                        wordWrapTxt.value = true
                    }
                    Settings.word_wrap = it
                },
            )

            EditorSettingsToggle(
                label = stringResource(strings.txt_word_wrap),
                description = stringResource(strings.txt_word_wrap_desc),
                isEnabled = !wordWrap.value,
                state = wordWrapTxt,
                sideEffect = {
                    wordWrapTxt.value = it
                    Settings.word_wrap_text = it
                },
            )

            EditorSettingsToggle(
                label = stringResource(strings.read_mode),
                description = stringResource(strings.read_mode_desc),
                default = Settings.read_only_default,
                sideEffect = { Settings.read_only_default = it },
            )
        }

        PreferenceGroup(heading = stringResource(id = strings.editor)) {
            EditorSettingsToggle(
                label = stringResource(strings.disable_virtual_kbd),
                description = stringResource(strings.disable_virtual_kbd_desc),
                default = Settings.hide_soft_keyboard_if_hardware,
                sideEffect = { Settings.hide_soft_keyboard_if_hardware = it },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.line_spacing),
                description = stringResource(id = strings.line_spacing_desc),
                showSwitch = false,
                default = false,
                sideEffect = { showLineSpacingDialog = true },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.cursor_anim),
                description = stringResource(id = strings.cursor_anim_desc),
                default = Settings.cursor_animation,
                sideEffect = { Settings.cursor_animation = it },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.show_line_number),
                description = stringResource(id = strings.show_line_number),
                default = Settings.show_line_numbers,
                sideEffect = { Settings.show_line_numbers = it },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.pin_line_number),
                description = stringResource(id = strings.pin_line_number),
                default = Settings.pin_line_number,
                sideEffect = { Settings.pin_line_number = it },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.render_whitespace),
                description = stringResource(id = strings.render_whitespace_desc),
                default = Settings.render_whitespace,
                sideEffect = { Settings.render_whitespace = it },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.show_suggestions),
                description = stringResource(id = strings.show_suggestions),
                default = Settings.show_suggestions,
                sideEffect = { Settings.show_suggestions = it },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.enable_sticky_scroll),
                description = stringResource(id = strings.enable_sticky_scroll_desc),
                default = Settings.sticky_scroll,
                sideEffect = { Settings.sticky_scroll = it },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.enable_quick_deletion),
                description = stringResource(id = strings.enable_quick_deletion_desc),
                default = Settings.quick_deletion,
                sideEffect = { Settings.quick_deletion = it },
            )

            NextScreenCard(
                label = stringResource(strings.manage_editor_font),
                description = stringResource(strings.manage_editor_font),
                route = SettingsRoutes.EditorFontScreen,
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.text_size),
                description = stringResource(id = strings.text_size_desc),
                showSwitch = false,
                default = false,
                sideEffect = { showTextSizeDialog = true },
            )

            EditorSettingsToggle(
                label = stringResource(strings.complete_on_enter),
                description = stringResource(strings.complete_on_enter_desc),
                default = Settings.complete_on_enter,
                sideEffect = { Settings.complete_on_enter = it },
            )

            SettingsToggle(
                label = stringResource(strings.text_mate_suggestion),
                description = stringResource(strings.text_mate_suggestion_desc),
                default = Settings.textmate_suggestions,
                sideEffect = { newValue ->
                    Settings.textmate_suggestions = newValue

                    scope.launch {
                        MainActivity.instance?.apply {
                            viewModel.tabs.filterIsInstance<EditorTab>().forEach { tab ->
                                val scope = tab.editorState.textmateScope ?: return@forEach
                                val language = tab.editorState.editor.get()?.editorLanguage as? TextMateLanguage

                                if (newValue) {
                                    val keywords = KeywordManager.getKeywords(scope)
                                    keywords?.let { language?.setCompleterKeywords(it.toTypedArray()) }
                                } else {
                                    language?.setCompleterKeywords(null)
                                }
                            }
                        }
                    }
                },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.tab_size),
                description = stringResource(id = strings.tab_size_desc),
                showSwitch = false,
                default = false,
                sideEffect = { showTabSizeDialog = true },
            )

            EditorSettingsToggle(
                label = stringResource(strings.use_tabs),
                description = stringResource(strings.use_tabs_desc),
                default = Settings.actual_tabs,
                sideEffect = {
                    Settings.actual_tabs = it

                    MainActivity.instance?.apply {
                        viewModel.tabs.filterIsInstance<EditorTab>().forEach { tab ->
                            val language = tab.editorState.editor.get()?.editorLanguage as? TextMateLanguage
                            language?.useTab(it)
                        }
                    }
                },
            )
        }

        PreferenceGroup(heading = stringResource(strings.actions)) {
            NextScreenCard(
                label = stringResource(strings.toolbar_actions),
                description = stringResource(strings.toolbar_actions_desc),
                route = SettingsRoutes.ToolbarActions,
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.extra_keys),
                description = stringResource(id = strings.extra_keys_desc),
                default = Settings.show_extra_keys,
                sideEffect = {
                    Settings.show_extra_keys = it
                    ReactiveSettings.update()
                },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.extra_key_bg),
                description = stringResource(id = strings.extra_key_bg_desc),
                isEnabled = ReactiveSettings.showExtraKeys,
                default = Settings.extra_keys_bg,
                sideEffect = {
                    Settings.extra_keys_bg = it
                    ReactiveSettings.update()
                },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.split_extra_keys),
                description = stringResource(id = strings.split_extra_keys_desc),
                isEnabled = ReactiveSettings.showExtraKeys,
                default = Settings.split_extra_keys,
                sideEffect = {
                    Settings.split_extra_keys = it
                    ReactiveSettings.update()
                },
            )

            NextScreenCard(
                label = stringResource(strings.change_extra_keys),
                description = stringResource(strings.change_extra_keys_desc),
                route = SettingsRoutes.ExtraKeys,
                isEnabled = ReactiveSettings.showExtraKeys,
            )
        }

        PreferenceGroup(heading = stringResource(strings.drawer)) {
            EditorSettingsToggle(
                label = stringResource(id = strings.keep_drawer_locked),
                description = stringResource(id = strings.drawer_lock_desc),
                default = Settings.keep_drawer_locked,
                sideEffect = { Settings.keep_drawer_locked = it },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.sort_mode),
                description = stringResource(id = strings.sort_mode_desc),
                showSwitch = false,
                sideEffect = { showSortingModeDialog = true },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.show_hidden_files_drawer),
                description = stringResource(id = strings.show_hidden_files_drawer_desc),
                default = Settings.show_hidden_files_drawer,
                sideEffect = {
                    Settings.show_hidden_files_drawer = it
                    ReactiveSettings.update()
                },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.compact_folders_drawer),
                description = stringResource(id = strings.compact_folders_drawer_desc),
                default = Settings.compact_folders_drawer,
                sideEffect = {
                    Settings.compact_folders_drawer = it
                    ReactiveSettings.update()
                },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.show_hidden_files_search),
                description = stringResource(id = strings.show_hidden_files_search_desc),
                default = Settings.show_hidden_files_search,
                sideEffect = { Settings.show_hidden_files_search = it },
            )

            EditorSettingsToggle(
                label = stringResource(strings.index_project),
                description = stringResource(strings.always_index_projects),
                default = Settings.always_index_projects,
                sideEffect = { Settings.always_index_projects = it },
            )

            EditorSettingsToggle(
                label = stringResource(strings.auto_open_new_files),
                description = stringResource(strings.auto_open_new_files_desc),
                default = Settings.auto_open_new_files,
                sideEffect = { Settings.auto_open_new_files = it },
            )
        }

        PreferenceGroup(heading = stringResource(strings.other)) {
            EditorSettingsToggle(
                label = stringResource(id = strings.restore_sessions),
                description = stringResource(id = strings.restore_sessions_desc),
                default = Settings.restore_sessions,
                sideEffect = { Settings.restore_sessions = it },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.smooth_tabs),
                description = stringResource(id = strings.smooth_tab_desc),
                default = Settings.smooth_tabs,
                sideEffect = { Settings.smooth_tabs = it },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.show_tab_icons),
                description = stringResource(id = strings.show_tab_icons_desc),
                default = Settings.show_tab_icons,
                sideEffect = { Settings.show_tab_icons = it },
            )

            NextScreenCard(
                label = stringResource(strings.default_encoding),
                description = stringResource(strings.default_encoding_desc),
                route = SettingsRoutes.DefaultEncoding,
            )

            NextScreenCard(
                label = stringResource(strings.line_ending),
                description = stringResource(strings.line_ending_desc),
                route = SettingsRoutes.DefaultLineEnding,
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.auto_save),
                description = stringResource(id = strings.auto_save_desc),
                default = Settings.auto_save,
                sideEffect = { Settings.auto_save = it },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.auto_save_delay),
                description = stringResource(id = strings.auto_save_delay_desc),
                showSwitch = false,
                sideEffect = { showAutoSaveDialog = true },
            )

            EditorSettingsToggle(
                label = stringResource(id = strings.enable_editorconfig),
                description = stringResource(id = strings.enable_editorconfig_desc),
                default = Settings.enable_editorconfig,
                sideEffect = {
                    Settings.enable_editorconfig = it
                    scope.launch { refreshEditorSettings() }
                },
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
                    scope.launch { refreshEditorSettings() }
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
                    scope.launch { refreshEditorSettings() }
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
                    scope.launch { refreshEditorSettings() }
                },
                onFinish = {
                    tabSizeValue = Settings.tab_size.toString()
                    tabSizeError = null
                    showTabSizeDialog = false
                },
            )
        }

        if (showAutoSaveDialog) {
            SingleInputDialog(
                title = stringResource(id = strings.auto_save_delay),
                inputLabel = stringResource(id = strings.auto_save_delay),
                inputValue = autoSaveDelayValue,
                errorMessage = autoSaveDelayError,
                onInputValueChange = {
                    autoSaveDelayValue = it
                    autoSaveDelayError = null
                    if (autoSaveDelayValue.toIntOrNull() == null) {
                        autoSaveDelayError = context.getString(strings.value_invalid)
                    } else if (autoSaveDelayValue.toInt() > 4000) {
                        autoSaveDelayError = context.getString(strings.value_large)
                    } else if (autoSaveDelayValue.toInt() < 5) {
                        autoSaveDelayError = context.getString(strings.value_small)
                    }
                },
                onConfirm = {
                    Settings.auto_save_delay = autoSaveDelayValue.toLong()
                    scope.launch { refreshEditorSettings() }
                },
                onFinish = {
                    autoSaveDelayValue = Settings.auto_save_delay.toString()
                    autoSaveDelayError = null
                    showAutoSaveDialog = false
                },
            )
        }

        if (showSortingModeDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSortingModeDialog = false
                    sortingModeValue = Settings.sort_mode
                },
                title = { Text(stringResource(strings.sort_mode)) },
                text = {
                    Column {
                        PreferenceTemplate(
                            modifier =
                                Modifier.clip(MaterialTheme.shapes.large).clickable {
                                    sortingModeValue = SortMode.SORT_BY_NAME.ordinal
                                },
                            title = { Text(stringResource(strings.sort_by_name)) },
                            startWidget = {
                                RadioButton(
                                    selected = sortingModeValue == SortMode.SORT_BY_NAME.ordinal,
                                    onClick = null,
                                )
                            },
                        )

                        PreferenceTemplate(
                            modifier =
                                Modifier.clip(MaterialTheme.shapes.large).clickable {
                                    sortingModeValue = SortMode.SORT_BY_SIZE.ordinal
                                },
                            title = { Text(stringResource(strings.sort_by_size)) },
                            startWidget = {
                                RadioButton(
                                    selected = sortingModeValue == SortMode.SORT_BY_SIZE.ordinal,
                                    onClick = null,
                                )
                            },
                        )

                        PreferenceTemplate(
                            modifier =
                                Modifier.clip(MaterialTheme.shapes.large).clickable {
                                    sortingModeValue = SortMode.SORT_BY_DATE.ordinal
                                },
                            title = { Text(stringResource(strings.sort_by_date)) },
                            startWidget = {
                                RadioButton(
                                    selected = sortingModeValue == SortMode.SORT_BY_DATE.ordinal,
                                    onClick = null,
                                )
                            },
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSortingModeDialog = false
                            Settings.sort_mode = sortingModeValue
                            fileTreeViewModel.get()?.apply {
                                sortMode = SortMode.entries[sortingModeValue]
                                viewModelScope.launch { refreshEverything() }
                            }
                        }
                    ) {
                        Text(stringResource(strings.apply))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSortingModeDialog = false
                            sortingModeValue = Settings.sort_mode
                        }
                    ) {
                        Text(stringResource(strings.cancel))
                    }
                },
            )
        }
    }
}

fun refreshEditors() {
    MainActivity.instance?.apply {
        viewModel.tabs.forEach {
            if (it is EditorTab) {
                it.refreshKey = nextInt()
            }
        }
    }
}

suspend fun refreshEditorSettings() {
    MainActivity.instance?.apply {
        viewModel.tabs.forEach {
            if (it is EditorTab) {
                it.reapplyEditorSettings()
            }
        }
    }
}
