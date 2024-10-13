package com.rk.settings

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.annotation.ChecksSdkIntAtLeast

object DefaultValues {
    val IS_USE_MONET = VERSION.SDK_INT >= VERSION_CODES.S
    const val IS_USE_AMOLED = false
    const val EDITOR_IS_USE_WORD_WRAP = false
    const val PIN_LINE_NUMBERS = false
    const val WORD_WRAP_ENABLED = false
    const val SHOW_ARROW_KEYS = false
    const val KEEP_DRAWER_LOCKED = false
    const val DIAGONAL_SCROLL = false
    const val SHOW_LINE_NUMBERS = false
    const val AUTO_SAVE_ENABLED = false
    const val CURSOR_ANIMATION_ENABLED = true
    const val IS_FAIL_SAFE = false
    const val ENABLE_LINK2SYMLINK = true
    const val ENABLE_ASHMEM_MEMFD = true
    const val ENABLE_SYSVIPC = true
    const val ENABLE_KILL_ON_EXIT = true
    const val FORCE_CHAR_IN_TERMINAL = false
    const val CTRL_WORKAROUND = false
    const val SHOW_SUGGESTIONS = false
    const val SHOW_VIRTUAL_KEYBOARD = false
    const val CHECK_UPDATE = true
    const val VIEWPAGER_SMOOTH_SCROLL = false
    
    const val TAB_SIZE = 4
    const val TEXT_SIZE = 14
    const val AUTO_SAVE_TIME_VALUE = 5000
    const val TERMINAL_TEXT_SIZE = 13
    
    const val LAST_UPDATE_CHECK = 0L
    
}
