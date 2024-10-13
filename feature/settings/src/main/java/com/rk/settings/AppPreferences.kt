package com.rk.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

// Boolean Preferences
@JvmField val appIsUseMonetPreference = booleanPreferencesKey("app_monet")
@JvmField val appIsUseAmoledPreference = booleanPreferencesKey("app_amoled")
@JvmField val editorIsUseWordWrapPreference = booleanPreferencesKey("editor_word_wrap")
@JvmField val pinLineNumbersPreference = booleanPreferencesKey("pin_line_numbers")
@JvmField val wordWrapEnabledPreference = booleanPreferencesKey("word_wrap_enabled")
@JvmField val cursorAnimationEnabledPreference = booleanPreferencesKey("cursor_animation_enabled")
@JvmField val showArrowKeysPreference = booleanPreferencesKey("show_arrow_keys")
@JvmField val keepDrawerLockedPreference = booleanPreferencesKey("keep_drawer_locked")
@JvmField val diagonalScrollPreference = booleanPreferencesKey("diagnol_scroll")
@JvmField val showLineNumbersPreference = booleanPreferencesKey("show_line_numbers")
@JvmField val autoSaveEnabledPreference = booleanPreferencesKey("auto_save_enabled")
@JvmField val fileTreeDiagonalScroll = booleanPreferencesKey("diagonal_scroll_file_tree")
@JvmField val isFailSafePreference = booleanPreferencesKey("fail_safe")
@JvmField val enableLink2symPreference = booleanPreferencesKey("link2sym")
@JvmField val enableAshmemMemfdPreference = booleanPreferencesKey("ashmem_memfd")
@JvmField val enableSysvipcPreference = booleanPreferencesKey("sysvipc")
@JvmField val enableKillOnExitPreference = booleanPreferencesKey("kill_on_exit")
@JvmField val enableForceCharInTerminalPreference = booleanPreferencesKey("force_char_in_terminal")
@JvmField val enableCtrlWorkaroundPreference = booleanPreferencesKey("ctrlworkaround")
@JvmField val enableShowSuggestionsPreference = booleanPreferencesKey("show_suggestions")
@JvmField val enableShowVirtualKeyboardPreference = booleanPreferencesKey("show_virtual_keyboard")
@JvmField val enableCheckUpdatePreference = booleanPreferencesKey("check_update")
@JvmField val enableViewPagerSmoothScrollPreference = booleanPreferencesKey("viewpager_smooth_scroll")

// Integer Preferences
@JvmField val tabSizePreference = intPreferencesKey("tab_size")
@JvmField val editorTextSizePreference = intPreferencesKey("text_size")
@JvmField val autoSaveTimePreference = intPreferencesKey("auto_save_time")
@JvmField val terminalTextSizePreference = intPreferencesKey("terminal_text_size")


// Long Preferences
@JvmField val lastUpdateCheckPreference = longPreferencesKey("last_update_check")