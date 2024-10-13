package com.rk.settings.viewmodels

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.settings.DefaultValues
import com.rk.settings.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppPreferencesViewModel(private val dataStore: DataStore<Preferences>) : ViewModel() {
    
    private fun <T> get(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return dataStore.data.map { preferences ->
            preferences[key] ?: defaultValue
        }
    }
    
    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[key] = value
            }
        }
    }
    
    // Boolean preference flows
    val appIsUseMonet = get(appIsUseMonetPreference, DefaultValues.IS_USE_MONET)
    val appIsUseAmoled = get(appIsUseAmoledPreference, DefaultValues.IS_USE_AMOLED)
    val editorIsUseWordWrap = get(editorIsUseWordWrapPreference, DefaultValues.EDITOR_IS_USE_WORD_WRAP)
    val pinLineNumbers = get(pinLineNumbersPreference, DefaultValues.PIN_LINE_NUMBERS)
    val wordWrapEnabled = get(wordWrapEnabledPreference, DefaultValues.WORD_WRAP_ENABLED)
    val cursorAnimationEnabled = get(cursorAnimationEnabledPreference, DefaultValues.CURSOR_ANIMATION_ENABLED)
    val showArrowKeys = get(showArrowKeysPreference, DefaultValues.SHOW_ARROW_KEYS)
    val keepDrawerLocked = get(keepDrawerLockedPreference, DefaultValues.KEEP_DRAWER_LOCKED)
    val diagonalScroll = get(diagonalScrollPreference, DefaultValues.DIAGONAL_SCROLL)
    val showLineNumbers = get(showLineNumbersPreference, DefaultValues.SHOW_LINE_NUMBERS)
    val autoSaveEnabled = get(autoSaveEnabledPreference, DefaultValues.AUTO_SAVE_ENABLED)
    val diagonalScrollEnabled = get(fileTreeDiagonalScroll, DefaultValues.DIAGONAL_SCROLL)
    val isFailSafe = get(isFailSafePreference, DefaultValues.IS_FAIL_SAFE)
    val link2sym = get(enableLink2symPreference, DefaultValues.ENABLE_LINK2SYMLINK)
    val ashmemMemfd = get(enableAshmemMemfdPreference, DefaultValues.ENABLE_ASHMEM_MEMFD)
    val sysvipc = get(enableSysvipcPreference, DefaultValues.ENABLE_SYSVIPC)
    val killOnExit = get(enableKillOnExitPreference, DefaultValues.ENABLE_KILL_ON_EXIT)
    val forceCharInTerminal = get(enableForceCharInTerminalPreference, DefaultValues.FORCE_CHAR_IN_TERMINAL)
    val ctrlWorkaround = get(enableCtrlWorkaroundPreference, DefaultValues.CTRL_WORKAROUND)
    val showSuggestions = get(enableShowSuggestionsPreference, DefaultValues.SHOW_SUGGESTIONS)
    val showVirtualKeyboard = get(enableShowVirtualKeyboardPreference, DefaultValues.SHOW_VIRTUAL_KEYBOARD)
    val checkUpdate = get(enableCheckUpdatePreference, DefaultValues.CHECK_UPDATE)
    val viewPagerSmoothScroll = get(enableViewPagerSmoothScrollPreference, DefaultValues.VIEWPAGER_SMOOTH_SCROLL)
    
    // Integer preference flows
    val tabSize = get(tabSizePreference, DefaultValues.TAB_SIZE)
    val editorTextSize = get(editorTextSizePreference, DefaultValues.TEXT_SIZE)
    val autoSaveTime = get(autoSaveTimePreference, DefaultValues.AUTO_SAVE_TIME_VALUE)
    val terminalTextSize = get(terminalTextSizePreference, DefaultValues.TERMINAL_TEXT_SIZE)
    
    // Long preference flows
    val lastUpdate = get(lastUpdateCheckPreference, DefaultValues.LAST_UPDATE_CHECK)
    
    // Functions to update boolean preferences
    inline fun enableMonet(value: Boolean) = set(appIsUseMonetPreference, value)
    inline fun enableAmoled(value: Boolean) = set(appIsUseAmoledPreference, value)
    inline fun enableEditorWordWrap(value: Boolean) = set(editorIsUseWordWrapPreference, value)
    inline fun enablePinLineNumbers(value: Boolean) = set(pinLineNumbersPreference, value)
    inline fun enableWordWrap(value: Boolean) = set(wordWrapEnabledPreference, value)
    inline fun enableCursorAnimation(value: Boolean) = set(cursorAnimationEnabledPreference, value)
    inline fun enableArrowKeys(value: Boolean) = set(showArrowKeysPreference, value)
    inline fun enableDrawerLock(value: Boolean) = set(keepDrawerLockedPreference, value)
    inline fun enableDiagonalScroll(value: Boolean) = set(diagonalScrollPreference, value)
    inline fun enableLineNumbers(value: Boolean) = set(showLineNumbersPreference, value)
    inline fun enableAutoSave(value: Boolean) = set(autoSaveEnabledPreference, value)
    inline fun enableFileTreeDiagonalScroll(value: Boolean) = set(fileTreeDiagonalScroll, value)
    
    // Functions to update integer preferences
    inline fun setTabSize(value: Int) = set(tabSizePreference, value)
    inline fun setEditorTextSize(value: Int) = set(editorTextSizePreference, value)
    inline fun setAutoSaveTime(value: Int) = set(autoSaveTimePreference, value)
    inline fun setTerminalTextSize(value: Int) = set(terminalTextSizePreference, value)
    
    // Functions to update long preferences
    inline fun setLastUpdate(value: Long) = set(lastUpdateCheckPreference, value)
}
