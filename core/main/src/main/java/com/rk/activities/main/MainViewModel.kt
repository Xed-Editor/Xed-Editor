package com.rk.activities.main

import androidx.compose.animation.core.Animatable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.activities.main.session.EditorManager
import com.rk.activities.main.session.SessionManager
import com.rk.activities.main.session.TabManager
import com.rk.commands.Command
import com.rk.extension.model.ExtensionManifest
import com.rk.file.FileObject
import com.rk.settings.Settings
import com.rk.tabs.base.Tab
import com.rk.tabs.editor.EditorTab
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

fun List<Tab>.filterEditorTabs() = filterIsInstance<EditorTab>()

fun List<EditorTab>.filesByTab(): Map<EditorTab, FileObject> = mapNotNull { tab ->
    tab.file?.let { file -> tab to file }
}
    .toMap()

fun List<EditorTab>.filterWithFiles(predicate: (EditorTab, FileObject) -> Boolean): List<EditorTab> {
    return filesByTab()
        .filter { (tab, file) ->
            predicate(tab, file)
        }
        .map { it.key }
}

data class PendingExtensionInstall(
    val manifest: ExtensionManifest,
    val packageFile: File,
    val icon: File,
)

class MainViewModel : ViewModel() {
    val tabManager = TabManager()
    val editorManager = EditorManager(this)

    val tabs
        get() = tabManager.tabs

    val currentTab
        get() = tabManager.currentTab

    val currentTabIndex
        get() = tabManager.currentTabIndex

    val editorTabs
        get() = editorManager.tabs

    private val _showTopBar = MutableStateFlow(true)
    val showTopBar = _showTopBar.asStateFlow()

    private val _showCommandPalette = MutableStateFlow(false)
    val showCommandPalette = _showCommandPalette.asStateFlow()

    private val _isDraggingPalette = MutableStateFlow(false)
    val isDraggingPalette = _isDraggingPalette.asStateFlow()
    var draggingPaletteProgress = Animatable(0f)

    private val _commandPaletteInitialChildCommands = MutableStateFlow<List<Command>?>(null)
    val commandPaletteInitialChildCommands = _commandPaletteInitialChildCommands.asStateFlow()

    private val _commandPaletteInitialPlaceholder = MutableStateFlow<String?>(null)
    val commandPaletteInitialPlaceholder = _commandPaletteInitialPlaceholder.asStateFlow()

    private val _pendingExtensionInstall = MutableStateFlow<PendingExtensionInstall?>(null)

    val pendingExtensionInstall: StateFlow<PendingExtensionInstall?> = _pendingExtensionInstall

    fun setShowTopBar(value: Boolean) {
        _showTopBar.value = value
    }

    fun setDraggingPalette(value: Boolean) {
        _isDraggingPalette.value = value
    }

    fun openExtensionIntentDialog(manifest: ExtensionManifest, file: File, icon: File) {
        _pendingExtensionInstall.value = PendingExtensionInstall(manifest, file, icon)
    }

    fun closeExtensionIntentDialog() {
        val pendingInstall = _pendingExtensionInstall.value

        viewModelScope.launch(Dispatchers.Main) {
            _pendingExtensionInstall.value = null

            withContext(Dispatchers.IO) {
                pendingInstall?.icon?.delete()
                pendingInstall?.packageFile?.delete()
            }
        }
    }

    fun showCommandPalette() {
        _showCommandPalette.value = true
        _commandPaletteInitialChildCommands.value = null
        _commandPaletteInitialPlaceholder.value = null
    }

    fun showCommandPaletteWithChildren(placeholder: String? = null, childCommands: List<Command>) {
        _showCommandPalette.value = true
        _commandPaletteInitialChildCommands.value = childCommands
        _commandPaletteInitialPlaceholder.value = placeholder
    }

    suspend fun closeCommandPalette() {
        _isDraggingPalette.value = false
        draggingPaletteProgress.snapTo(0f)
        _showCommandPalette.value = false
        _commandPaletteInitialChildCommands.value = null
        _commandPaletteInitialPlaceholder.value = null
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

    fun getNextUntitledTitle(): String {
        val titles = tabs.map { it.title }.toSet()
        var i = 1
        while (titles.contains("Untitled-$i")) {
            i++
        }
        return "Untitled-$i"
    }
}
