package com.rk.settings.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.rk.activities.main.MainActivity
import com.rk.activities.main.ui.fileTreeViewModel
import com.rk.activities.settings.SettingsRoutes
import com.rk.activities.settings.settingsNavController
import com.rk.components.EditorSettingsItem
import com.rk.components.NextScreenCard
import com.rk.components.PreferenceList
import com.rk.components.PreferenceSingleInput
import com.rk.components.SettingsItem
import com.rk.components.SmoothValueSlider
import com.rk.components.SteppedValueSlider
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.editor.KeywordManager
import com.rk.filetree.SortMode
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import kotlinx.coroutines.launch

@Composable
fun SettingsEditorScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.editor), backArrowVisible = true) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        PreferenceGroup(heading = stringResource(strings.language_server)) {
            NextScreenCard(
                navController = navController,
                label = stringResource(strings.manage_language_servers),
                description = stringResource(strings.manage_language_servers_desc),
                route = SettingsRoutes.LspSettings,
            )

            EditorSettingsItem(
                label = stringResource(strings.insert_final_newline),
                description = stringResource(strings.insert_final_newline_desc),
                default = Settings.insert_final_newline,
                sideEffect = { Settings.insert_final_newline = it },
            )

            EditorSettingsItem(
                label = stringResource(strings.trim_trailing_whitespace),
                description = stringResource(strings.trim_trailing_whitespace_desc),
                default = Settings.trim_trailing_whitespace,
                sideEffect = { Settings.trim_trailing_whitespace = it },
            )
        }

        PreferenceGroup(heading = stringResource(strings.formatting)) {
            NextScreenCard(
                label = stringResource(strings.manage_formatters),
                description = stringResource(strings.manage_formatters_desc),
                onClick = { navController.navigate(SettingsRoutes.Formatters.route) },
            )

            EditorSettingsItem(
                label = stringResource(strings.format_on_save),
                description = stringResource(strings.format_on_save_desc),
                default = Settings.format_on_save,
                sideEffect = { Settings.format_on_save = it },
            )
        }

        PreferenceGroup(heading = stringResource(strings.intelligent_features)) {
            EditorSettingsItem(
                label = stringResource(strings.auto_close_tags),
                description = stringResource(strings.auto_close_tags_desc),
                default = Settings.auto_close_tags,
                sideEffect = {
                    Settings.auto_close_tags = it
                    refreshEditors()
                },
            )

            EditorSettingsItem(
                label = stringResource(strings.bullet_continuation),
                description = stringResource(strings.bullet_continuation_desc),
                default = Settings.bullet_continuation,
                sideEffect = {
                    Settings.bullet_continuation = it
                    refreshEditors()
                },
            )

            EditorSettingsItem(
                label = stringResource(strings.show_color_previews),
                description = stringResource(strings.show_color_previews_desc),
                default = Settings.show_color_previews,
                sideEffect = {
                    Settings.show_color_previews = it
                    refreshEditors()
                },
            )
        }

        PreferenceGroup(heading = stringResource(strings.content)) {
            val wordWrap = remember { mutableStateOf(Settings.word_wrap) }
            val wordWrapTxt = remember { mutableStateOf(Settings.word_wrap_text || Settings.word_wrap) }

            EditorSettingsItem(
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

            EditorSettingsItem(
                label = stringResource(strings.txt_word_wrap),
                description = stringResource(strings.txt_word_wrap_desc),
                isEnabled = !wordWrap.value,
                state = wordWrapTxt,
                sideEffect = {
                    wordWrapTxt.value = it
                    Settings.word_wrap_text = it
                },
            )

            EditorSettingsItem(
                label = stringResource(strings.read_mode),
                description = stringResource(strings.read_mode_desc),
                default = Settings.read_only_default,
                sideEffect = { Settings.read_only_default = it },
            )
        }

        PreferenceGroup(heading = stringResource(id = strings.editor)) {
            EditorSettingsItem(
                label = stringResource(strings.disable_virtual_kbd),
                description = stringResource(strings.disable_virtual_kbd_desc),
                default = Settings.hide_soft_keyboard_if_hardware,
                sideEffect = { Settings.hide_soft_keyboard_if_hardware = it },
            )

            PreferenceSingleInput(
                label = stringResource(id = strings.line_spacing),
                description = stringResource(id = strings.line_spacing_desc),
                value = Settings.line_spacing.toString(),
                validate = {
                    if (it.toFloatOrNull() == null) {
                        context.getString(strings.value_invalid)
                    } else if (it.toFloat() < 0.6f) {
                        context.getString(strings.value_small)
                    } else {
                        null
                    }
                },
                onConfirm = {
                    Settings.line_spacing = it.toFloat()
                    scope.launch { refreshEditorSettings() }
                },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.cursor_anim),
                description = stringResource(id = strings.cursor_anim_desc),
                default = Settings.cursor_animation,
                sideEffect = { Settings.cursor_animation = it },
            )

            EditorSettingsItem(
                label = stringResource(strings.show_minimap),
                description = stringResource(strings.show_minimap_desc),
                default = Settings.show_minimap,
                sideEffect = { Settings.show_minimap = it },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.show_line_number),
                description = stringResource(id = strings.show_line_number),
                default = Settings.show_line_numbers,
                sideEffect = { Settings.show_line_numbers = it },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.pin_line_number),
                description = stringResource(id = strings.pin_line_number),
                default = Settings.pin_line_number,
                sideEffect = { Settings.pin_line_number = it },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.render_whitespace),
                description = stringResource(id = strings.render_whitespace_desc),
                default = Settings.render_whitespace,
                sideEffect = { Settings.render_whitespace = it },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.show_suggestions),
                description = stringResource(id = strings.show_suggestions),
                default = Settings.show_suggestions,
                sideEffect = { Settings.show_suggestions = it },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.enable_sticky_scroll),
                description = stringResource(id = strings.enable_sticky_scroll_desc),
                default = Settings.sticky_scroll,
                sideEffect = { Settings.sticky_scroll = it },
            )

            EditorSettingsItem(
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

            SmoothValueSlider(
                label = stringResource(id = strings.text_size),
                description = stringResource(id = strings.text_size_desc),
                default = Settings.editor_text_size,
                min = 6,
                max = 50,
            ) {
                Settings.editor_text_size = it
                scope.launch { refreshEditorSettings() }
            }

            EditorSettingsItem(
                label = stringResource(strings.auto_closing_bracket),
                description = stringResource(strings.auto_closing_bracket_desc),
                default = Settings.auto_closing_bracket,
                sideEffect = { Settings.auto_closing_bracket = it },
            )

            EditorSettingsItem(
                label = stringResource(strings.complete_on_enter),
                description = stringResource(strings.complete_on_enter_desc),
                default = Settings.complete_on_enter,
                sideEffect = { Settings.complete_on_enter = it },
            )

            SettingsItem(
                label = stringResource(strings.text_mate_suggestion),
                description = stringResource(strings.text_mate_suggestion_desc),
                default = Settings.textmate_suggestions,
                sideEffect = { newValue ->
                    Settings.textmate_suggestions = newValue

                    scope.launch {
                        MainActivity.instance?.apply {
                            viewModel.tabs.filterIsInstance<EditorTab>().forEach { tab ->
                                val scope = tab.editorState.textmateScope ?: return@forEach
                                val textMateLanguage = tab.editorState.editor.get()?.getTextMateLanguage()

                                if (newValue) {
                                    val keywords = KeywordManager.getKeywords(scope)
                                    keywords?.let { textMateLanguage?.setCompleterKeywords(it.toTypedArray()) }
                                } else {
                                    textMateLanguage?.setCompleterKeywords(null)
                                }
                            }
                        }
                    }
                },
            )

            SteppedValueSlider(
                label = stringResource(id = strings.tab_size),
                description = stringResource(id = strings.tab_size_desc),
                default = Settings.tab_size,
                min = 1,
                max = 16,
            ) {
                Settings.tab_size = it

                MainActivity.instance?.apply {
                    viewModel.tabs.filterIsInstance<EditorTab>().forEach { tab ->
                        val textMateLanguage = tab.editorState.editor.get()?.getTextMateLanguage()
                        textMateLanguage?.tabSize = it
                    }
                }

                scope.launch { refreshEditorSettings() }
            }

            EditorSettingsItem(
                label = stringResource(strings.use_tabs),
                description = stringResource(strings.use_tabs_desc),
                default = Settings.actual_tabs,
                sideEffect = {
                    Settings.actual_tabs = it

                    MainActivity.instance?.apply {
                        viewModel.tabs.filterIsInstance<EditorTab>().forEach { tab ->
                            val textMateLanguage = tab.editorState.editor.get()?.getTextMateLanguage()
                            textMateLanguage?.useTab(it)
                        }
                    }

                    scope.launch { refreshEditorSettings() }
                },
            )
        }

        PreferenceGroup(heading = stringResource(strings.actions)) {
            NextScreenCard(
                label = stringResource(strings.toolbar_actions),
                description = stringResource(strings.toolbar_actions_desc),
                route = SettingsRoutes.ToolbarActions,
            )

            EditorSettingsItem(
                label = stringResource(id = strings.extra_keys),
                description = stringResource(id = strings.extra_keys_desc),
                default = Settings.show_extra_keys,
                sideEffect = { Settings.show_extra_keys = it },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.extra_key_bg),
                description = stringResource(id = strings.extra_key_bg_desc),
                isEnabled = Settings.show_extra_keys,
                default = Settings.extra_keys_bg,
                sideEffect = { Settings.extra_keys_bg = it },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.split_extra_keys),
                description = stringResource(id = strings.split_extra_keys_desc),
                isEnabled = Settings.show_extra_keys,
                default = Settings.split_extra_keys,
                sideEffect = { Settings.split_extra_keys = it },
            )

            NextScreenCard(
                label = stringResource(strings.change_extra_keys),
                description = stringResource(strings.change_extra_keys_desc),
                route = SettingsRoutes.ExtraKeys,
                isEnabled = Settings.show_extra_keys,
            )
        }

        PreferenceGroup(heading = stringResource(strings.drawer)) {
            EditorSettingsItem(
                label = stringResource(id = strings.keep_drawer_locked),
                description = stringResource(id = strings.drawer_lock_desc),
                default = Settings.keep_drawer_locked,
                sideEffect = { Settings.keep_drawer_locked = it },
            )

            PreferenceList(
                label = stringResource(id = strings.sort_mode),
                description = stringResource(id = strings.sort_mode_desc),
                items = SortMode.entries.map { it to stringResource(it.stringRes) },
                selectedItem = SortMode.entries[Settings.sort_mode],
                onItemSelected = { sortMode ->
                    Settings.sort_mode = sortMode.ordinal
                    fileTreeViewModel.get()?.apply {
                        setSortMode(sortMode)
                        viewModelScope.launch { refreshEverything() }
                    }
                },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.show_hidden_files_drawer),
                description = stringResource(id = strings.show_hidden_files_drawer_desc),
                default = Settings.show_hidden_files_drawer,
                sideEffect = { Settings.show_hidden_files_drawer = it },
            )

            NextScreenCard(
                label = stringResource(strings.exclude_files_drawer),
                description = stringResource(strings.exclude_files_drawer_desc),
                onClick = { settingsNavController.get()!!.navigate("${SettingsRoutes.ExcludeFiles.route}/true") },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.compact_folders_drawer),
                description = stringResource(id = strings.compact_folders_drawer_desc),
                default = Settings.compact_folders_drawer,
                sideEffect = { Settings.compact_folders_drawer = it },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.show_hidden_files_search),
                description = stringResource(id = strings.show_hidden_files_search_desc),
                default = Settings.show_hidden_files_search,
                sideEffect = { Settings.show_hidden_files_search = it },
            )

            EditorSettingsItem(
                label = stringResource(strings.always_index_projects),
                description = stringResource(strings.always_index_projects_desc),
                default = Settings.always_index_projects,
                sideEffect = { Settings.always_index_projects = it },
            )

            NextScreenCard(
                label = stringResource(strings.exclude_files_search),
                description = stringResource(strings.exclude_files_search_desc),
                onClick = { settingsNavController.get()!!.navigate("${SettingsRoutes.ExcludeFiles.route}/false") },
            )

            EditorSettingsItem(
                label = stringResource(strings.auto_open_new_files),
                description = stringResource(strings.auto_open_new_files_desc),
                default = Settings.auto_open_new_files,
                sideEffect = { Settings.auto_open_new_files = it },
            )
        }

        PreferenceGroup(heading = stringResource(strings.other)) {
            EditorSettingsItem(
                label = stringResource(strings.detect_bin_files),
                description = stringResource(strings.detect_bin_files_desc),
                default = Settings.detect_bin_files,
                sideEffect = { Settings.detect_bin_files = it },
            )

            EditorSettingsItem(
                label = stringResource(strings.oom_prediction),
                description = stringResource(strings.oom_prediction_desc),
                default = Settings.oom_prediction,
                sideEffect = { Settings.oom_prediction = it },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.restore_sessions),
                description = stringResource(id = strings.restore_sessions_desc),
                default = Settings.restore_sessions,
                sideEffect = { Settings.restore_sessions = it },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.smooth_tabs),
                description = stringResource(id = strings.smooth_tab_desc),
                default = Settings.smooth_tabs,
                sideEffect = { Settings.smooth_tabs = it },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.scoped_tabs),
                description = stringResource(id = strings.scoped_tabs_desc),
                default = Settings.scoped_tabs,
                sideEffect = { Settings.scoped_tabs = it },
            )

            EditorSettingsItem(
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

            EditorSettingsItem(
                label = stringResource(id = strings.auto_save),
                description = stringResource(id = strings.auto_save_desc),
                default = Settings.auto_save,
                sideEffect = { Settings.auto_save = it },
            )

            PreferenceSingleInput(
                label = stringResource(id = strings.auto_save_delay),
                description = stringResource(id = strings.auto_save_delay_desc),
                value = Settings.auto_save_delay.toString(),
                validate = {
                    if (it.toIntOrNull() == null) {
                        context.getString(strings.value_invalid)
                    } else if (it.toInt() > 4000) {
                        context.getString(strings.value_large)
                    } else if (it.toInt() < 5) {
                        context.getString(strings.value_small)
                    } else {
                        null
                    }
                },
                onConfirm = {
                    Settings.auto_save_delay = it.toLong()
                    scope.launch { refreshEditorSettings() }
                },
            )

            EditorSettingsItem(
                label = stringResource(id = strings.enable_editorconfig),
                description = stringResource(id = strings.enable_editorconfig_desc),
                default = Settings.enable_editorconfig,
                sideEffect = {
                    Settings.enable_editorconfig = it
                    scope.launch { refreshEditorSettings() }
                },
            )
        }
    }
}

fun refreshEditors() {
    MainActivity.instance?.apply {
        viewModel.tabs.forEach {
            if (it is EditorTab) {
                it.refreshKey++
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
