package com.rk.activities.main

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    var showTopBar by mutableStateOf(true)

    var showCommandPalette by mutableStateOf(false)
        private set

    var isDraggingPalette by mutableStateOf(false)
    var draggingPaletteProgress = Animatable(0f)

    var commandPaletteInitialChildCommands by mutableStateOf<List<Command>?>(null)
        private set

    var commandPaletteInitialPlaceholder by mutableStateOf<String?>(null)
        private set

    var pendingExtensionManifest by mutableStateOf<ExtensionManifest?>(null)
        private set

    var pendingExtensionPackage by mutableStateOf<File?>(null)
        private set

    var pendingExtensionIcon by mutableStateOf<File?>(null)
        private set

    fun openExtensionIntentDialog(manifest: ExtensionManifest, file: File, icon: File) {
        pendingExtensionManifest = manifest
        pendingExtensionPackage = file
        pendingExtensionIcon = icon
    }

    fun closeExtensionIntentDialog() {
        val icon = pendingExtensionIcon
        val file = pendingExtensionPackage

        viewModelScope.launch(Dispatchers.Main) {
            pendingExtensionManifest = null
            pendingExtensionPackage = null
            pendingExtensionIcon = null

            withContext(Dispatchers.IO) {
                icon?.delete()
                file?.delete()
            }
        }
    }

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

    fun getNextUntitledTitle(): String {
        val titles = tabs.map { it.tabTitle }.toSet()
        var i = 1
        while (titles.contains("Untitled-$i")) {
            i++
        }
        return "Untitled-$i"
    }
}
