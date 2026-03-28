package com.rk.activities.main

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.commands.Command
import com.rk.settings.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    val tabManager = TabManager()
    val editorManager = EditorManager(this)

    val tabs
        get() = tabManager.tabs

    val currentTab
        get() = tabManager.currentTab

    val currentTabIndex
        get() = tabManager.currentTabIndex

    var showTopBar by mutableStateOf(true)

    var showCommandPalette by mutableStateOf(false)
        private set

    var isDraggingPalette by mutableStateOf(false)
    var draggingPaletteProgress = Animatable(0f)

    var commandPaletteInitialChildCommands by mutableStateOf<List<Command>?>(null)
        private set

    var commandPaletteInitialPlaceholder by mutableStateOf<String?>(null)
        private set

    fun showCommandPalette() {
        showCommandPalette = true
        commandPaletteInitialChildCommands = null
        commandPaletteInitialPlaceholder = null
    }

    fun showCommandPaletteWithChildren(placeholder: String? = null, childCommands: List<Command>) {
        showCommandPalette = true
        commandPaletteInitialChildCommands = childCommands
        commandPaletteInitialPlaceholder = placeholder
    }

    suspend fun closeCommandPalette() {
        isDraggingPalette = false
        draggingPaletteProgress.snapTo(0f)
        showCommandPalette = false
        commandPaletteInitialChildCommands = null
        commandPaletteInitialPlaceholder = null
    }

    private val sessionRestored = CompletableDeferred<Unit>()

    init {
        restoreSessionsIfNeeded()
    }

    suspend fun awaitSessionRestoration() {
        sessionRestored.await()
    }

    private fun restoreSessionsIfNeeded() {
        if (Settings.restore_sessions) {
            viewModelScope.launch(Dispatchers.IO) {
                restoreTabs()
                sessionRestored.complete(Unit)
            }
        } else {
            sessionRestored.complete(Unit)
        }
    }

    /**
     * Restores tabs from the previous session if session restoration is enabled. It loads the preloaded session state,
     * restores each tab, and sets the active tab index.
     */
    private suspend fun restoreTabs() {
        SessionManager.mutex.withLock {
            val session = SessionManager.preloadedSession ?: return

            val deferredRestoredTabs = session.tabStates.mapNotNull { tabState -> tabState.toTab() }

            withContext(Dispatchers.Main) {
                deferredRestoredTabs.forEach { tabManager.addTab(it, false) }
                tabManager.setCurrentTab(session.currentTabIndex)
            }
        }
    }
}
