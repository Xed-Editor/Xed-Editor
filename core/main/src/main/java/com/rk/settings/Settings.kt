package com.rk.settings

import androidx.appcompat.app.AppCompatDelegate
import com.rk.filetree.SortMode
import com.rk.settings.editor.DEFAULT_ACTION_ITEMS
import com.rk.settings.editor.DEFAULT_EXCLUDED_FILES_DRAWER
import com.rk.settings.editor.DEFAULT_EXCLUDED_FILES_SEARCH
import com.rk.settings.editor.DEFAULT_EXTRA_KEYS_COMMANDS
import com.rk.settings.editor.DEFAULT_EXTRA_KEYS_SYMBOLS
import com.rk.settings.terminal.DEFAULT_TERMINAL_EXTRA_KEYS
import com.rk.theme.blueberry
import com.rk.utils.application
import com.rk.utils.hasHardwareKeyboard
import com.rk.xededitor.BuildConfig
import com.termux.terminal.TerminalEmulator
import java.nio.charset.Charset

object Settings {

    // ─── Editor Display ───────────────────────────────────────────────
    var tab_size by CachedPreference("tab_size", 4)
    var editor_text_size by CachedPreference("text_size", 14)
    var word_wrap by CachedPreference("word_wrap", false)
    var word_wrap_text by CachedPreference("word_wrap_text", true)
    var line_spacing by CachedPreference("line_spacing", 1f)
    var line_ending by CachedPreference("line_ending", "lf")
    var encoding by CachedPreference("encoding", Charset.defaultCharset().name())
    var show_line_numbers by CachedPreference("show_line_number", true)
    var pin_line_number by CachedPreference("pin_line_number", false)
    var render_whitespace by CachedPreference("render_whitespace", false)
    var sticky_scroll by CachedPreference("sticky_scroll", true)
    var cursor_animation by CachedPreference("cursor_animation", true)
    var show_minimap by CachedPreference("show_minimap", false)

    // ─── Editor Behavior ─────────────────────────────────────────────
    var quick_deletion by CachedPreference("fast_delete", true)
    var auto_save by CachedPreference("auto_save", false)
    var auto_save_delay by CachedPreference("auto_save_delay", 400L)
    var format_on_save by CachedPreference("format_on_save", false)
    var auto_close_tags by CachedPreference("auto_close_tags", true)
    var bullet_continuation by CachedPreference("bullet_continuation", true)
    var insert_final_newline by CachedPreference("insert_final_newline", true)
    var trim_trailing_whitespace by CachedPreference("trim_trailing_whitespace", true)
    var enable_editorconfig by CachedPreference("enable_editorconfig", true)
    var actual_tabs by CachedPreference("actual_tab", false)
    var show_suggestions by CachedPreference("show_suggestions", false)
    var textmate_suggestions by CachedPreference("textmate_suggestions", true)
    var complete_on_enter by CachedPreference("complete_on_enter", true)
    var auto_closing_bracket by CachedPreference("auto_closing_bracket", true)
    var read_only_default by CachedPreference("read_only_default", false)

    // ─── Editor Font ─────────────────────────────────────────────────
    var editor_font_path by CachedPreference("selected_font_path", "")
    var is_editor_font_asset by CachedPreference("is_font_asset", false)

    // ─── App Font ────────────────────────────────────────────────────
    var app_font_path by CachedPreference("app_font_path", "")
    var is_app_font_asset by CachedPreference("is_app_font_asset", false)

    // ─── Terminal ────────────────────────────────────────────────────
    var terminal_font_size by CachedPreference("terminal_font_size", 13)
    var terminal_font_path by CachedPreference("terminal_font_path", "")
    var is_terminal_font_asset by CachedPreference("is_terminal_font_asset", false)
    var terminal_scrollback_buffer by
        CachedPreference("terminal_scrollback_buffer", TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS)
    var terminal_cursor_style by CachedPreference("terminal_cursor_style", "block")
    var terminal_extra_keys by CachedPreference("terminal_extra_keys", DEFAULT_TERMINAL_EXTRA_KEYS)
    var terminal_virus_notice by CachedPreference("terminal_virus_notice", false)
    var seccomp by CachedPreference("seccomp", false)
    var desktop_mode by CachedPreference("desktop_mode", false)
    var sandbox by CachedPreference("sandbox", true)
    var terminate_sessions_on_exit by CachedPreference("terminate_sessions_on_exit", false)

    // ─── Theme ───────────────────────────────────────────────────────
    var theme by CachedPreference("theme", blueberry.id)
    var theme_mode by CachedPreference("default_night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    var amoled by CachedPreference("amoled", false)
    var monet by CachedPreference("monet", false)
    var theme_flipper by CachedPreference("theme_flipper", false)
    var fullscreen by CachedPreference("fullscreen", false)

    // ─── UI / Layout ─────────────────────────────────────────────────
    var show_extra_keys by CachedPreference("show_extra_keys", application?.let { hasHardwareKeyboard(it).not() } ?: true)
    var extra_keys_symbols by CachedPreference("extra_keys_symbols", DEFAULT_EXTRA_KEYS_SYMBOLS)
    var extra_keys_commands by CachedPreference("extra_keys_commands", DEFAULT_EXTRA_KEYS_COMMANDS)
    var split_extra_keys by CachedPreference("split_extra_keys", false)
    var extra_keys_bg by CachedPreference("extra_keys_bg", false)
    var action_items by CachedPreference("action_items", DEFAULT_ACTION_ITEMS)
    var last_used_command by CachedPreference("last_used_command", "")
    var show_tab_icons by CachedPreference("show_tab_icons", true)
    var smooth_tabs by CachedPreference("smooth_tab", false)
    var keep_drawer_locked by CachedPreference("drawer_lock", false)
    var hide_soft_keyboard_if_hardware by CachedPreference("always_show_soft_keyboard", true)
    var smart_toolbar by CachedPreference("smart_toolbar", false)

    // ─── AI / Agent ──────────────────────────────────────────────────
    var ai_agent by CachedPreference("ai_agent", "gemini")
    var ai_api_key by CachedPreference("ai_api_key", "")
    var ai_inline_completion by CachedPreference("ai_inline_completion", true)
    var ai_completion_url by CachedPreference("ai_completion_url", "")
    var ai_auto_apply by CachedPreference("ai_auto_apply", false)
    var ai_profiles_json by CachedPreference("ai_profiles_json", "")
    var ai_agent_extra_args by CachedPreference("ai_agent_extra_args", "")
    var ai_project_config_enabled by CachedPreference("ai_project_config_enabled", true)

    // ─── File Tree ───────────────────────────────────────────────────
    var sort_mode by CachedPreference("sort_mode", SortMode.SORT_BY_NAME.ordinal)
    var show_hidden_files_drawer by CachedPreference("show_hidden_files_drawer", true)
    var compact_folders_drawer by CachedPreference("compact_folders_drawer", true)
    var show_hidden_files_search by CachedPreference("show_hidden_files_search", false)
    var excluded_files_search by
        CachedPreference("excluded_files_search", DEFAULT_EXCLUDED_FILES_SEARCH.joinToString("\n"))
    var excluded_files_drawer by
        CachedPreference("excluded_files_drawer", DEFAULT_EXCLUDED_FILES_DRAWER.joinToString("\n"))
    var file_mask by CachedPreference("file_mask", "")

    // ─── Git ─────────────────────────────────────────────────────────
    var git_username by CachedPreference("git_username", "")
    var git_password by CachedPreference("git_password", "")
    var git_name by CachedPreference("git_name", "")
    var git_email by CachedPreference("git_email", "")
    var git_colorize_names by CachedPreference("git_colorize_names", true)
    var git_submodules by CachedPreference("git_submodules", true)
    var git_recursive_submodules by CachedPreference("git_recursive_submodules", true)

    // ─── Runner ──────────────────────────────────────────────────────
    var enable_html_runner by CachedPreference("enable_html_runner", true)
    var enable_md_runner by CachedPreference("enable_md_runner", true)
    var enable_universal_runner by CachedPreference("enable_universal_runner", true)
    var http_server_port by CachedPreference("http_server_port", 8357)
    var launch_in_browser by CachedPreference("launch_in_browser", false)
    var inject_eruda by CachedPreference("inject_eruda", true)

    // ─── Session ─────────────────────────────────────────────────────
    var restore_sessions by CachedPreference("restore_sessions", true)
    var selected_project by CachedPreference("selected_project", "")
    var project_as_pwd by CachedPreference("project_as_pwd", true)
    var expose_home_dir by CachedPreference("expose_home_dir", false)
    var auto_open_new_files by CachedPreference("auto_open_new_files", true)

    // ─── General ─────────────────────────────────────────────────────
    var current_lang by
        CachedPreference("current_lang", application?.resources?.configuration?.locales?.get(0)?.language ?: "en")
    var shown_disclaimer by CachedPreference("shown_disclaimer", false)
    var detect_bin_files by CachedPreference("detect_bin_files", true)
    var oom_prediction by CachedPreference("disable_oom_prediction", false)
    var ignore_storage_permission by CachedPreference("ignore_storage_permission", false)
    var has_shown_private_data_dir_warning by CachedPreference("has_shown_private_data_dir_warning", false)
    var has_shown_terminal_dir_warning by CachedPreference("has_shown_terminal_dir_warning", false)
    var confirm_exit by CachedPreference("confirm_exit", true)
    var verbose_error by CachedPreference("verbose_error", BuildConfig.DEBUG)
    var font_gson by CachedPreference("selected_font", "")
    var icon_pack by CachedPreference("icon_pack", "")

    // ─── Debug / Dev ──────────────────────────────────────────────────
    var check_for_update by CachedPreference("check_update", false)
    var github by CachedPreference("github", true)
    var anr_watchdog by CachedPreference("anr", BuildConfig.DEBUG)
    var strict_mode by CachedPreference("strict_mode", BuildConfig.DEBUG)
    var always_index_projects by CachedPreference("always_index_projects", false)

    // ─── Donation ────────────────────────────────────────────────────
    var donated by CachedPreference("donated", false)
    var user_declined_value by CachedPreference("user_declined_value", false)
    var user_said_maybe_later by CachedPreference("user_said_maybe_later", false)
    var user_has_supported by CachedPreference("user_has_supported", false)
    var donation_ask_count by CachedPreference("donation_ask_count", 0)
    var saves by CachedPreference("saves", 0)
    var runs by CachedPreference("runs", 0)
    var last_donation_dialog_timestamp by CachedPreference("last_donation_dialog_timestamp", 0L)

    // ─── Update ──────────────────────────────────────────────────────
    var last_update_check_timestamp by CachedPreference("last_update", 0L)
    var last_version_code by CachedPreference("last_version_code", -1L)
}
