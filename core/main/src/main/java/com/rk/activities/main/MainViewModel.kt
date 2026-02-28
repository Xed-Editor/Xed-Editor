package com.rk.activities.main

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.commands.Command
import com.rk.commands.CommandProvider
import com.rk.commands.KeybindingsManager
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.base.Tab
import com.rk.tabs.base.TabRegistry
import com.rk.tabs.editor.EditorTab
import com.rk.utils.application
import com.rk.utils.dialog
import com.rk.utils.expectOOM
import com.rk.utils.toast
import io.github.rosemoe.sora.event.SelectionChangeEvent
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Represents the state of a user's session, which can be serialized and saved. This allows for restoring the open tabs
 * and their states when the application is restarted.
 *
 * @property tabStates A list containing the state of each open tab. Each element in the list is a [TabState] object,
 *   which holds the specific information needed to restore a single tab.
 * @property currentTabIndex The index of the tab that was active when the session was saved. This is used to restore
 *   the user's focus to the correct tab.
 */
data class SessionState(val tabStates: List<TabState>, val currentTabIndex: Int) : Serializable

/**
 * Manages the saving and loading of the user's session state.
 *
 * This singleton object is responsible for persisting the state of open tabs and the currently selected tab to a cache
 * file. This allows the application to restore the previous session when it is restarted.
 *
 * Session state is stored in a file named "session" within the application's cache directory. Operations are
 * synchronized using a [Mutex] to ensure thread safety.
 */
object SessionManager {
    val mutex = Mutex()
    var preloadedSession: SessionState? = null
    var tabCacheFile = application!!.filesDir.child("session")

    suspend fun preloadSession() =
        mutex.withLock {
            runCatching {
                    if (tabCacheFile.exists() && tabCacheFile.canRead()) {
                        ObjectInputStream(FileInputStream(tabCacheFile)).use { ois ->
                            preloadedSession = ois.readObject() as? SessionState
                        }
                    }
                }
                .onFailure { it.printStackTrace() }
        }

    suspend fun saveSession(tabs: List<Tab>, currentTabIndex: Int) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                        val tabStates = tabs.mapNotNull { it.getState() }
                        val sessionState = SessionState(tabStates, currentTabIndex)

                        ObjectOutputStream(FileOutputStream(tabCacheFile)).use { oos -> oos.writeObject(sessionState) }
                    }
                    .onFailure {
                        it.printStackTrace()
                        toast(strings.save_tabs_error)
                    }
            }
        }
}

class MainViewModel : ViewModel() {
    val tabs = mutableStateListOf<Tab>()
    val mutex = Mutex()
    var currentTabIndex by mutableIntStateOf(0)

    var showCommandPalette by mutableStateOf(false)
    var draggingPaletteProgress = Animatable(0f)
    var isDraggingPalette by mutableStateOf(false)

    var commandPaletteInitialChildCommands by mutableStateOf<List<Command>?>(null)
    var commandPaletteInitialPlaceholder by mutableStateOf<String?>(null)

    fun showCommandPaletteWithChildren(placeholder: String? = null, childCommands: List<Command>) {
        commandPaletteInitialChildCommands = childCommands
        commandPaletteInitialPlaceholder = placeholder
        showCommandPalette = true
    }

    val currentTab: Tab?
        get() = tabs.getOrNull(currentTabIndex)

    val sessionRestored = CompletableDeferred<Unit>()

    init {
        if (Settings.restore_sessions) {
            viewModelScope.launch(Dispatchers.IO) {
                restoreTabs()
                sessionRestored.complete(Unit)
            }
        } else {
            sessionRestored.complete(Unit)
        }

        CommandProvider.buildCommands(this)
        KeybindingsManager.loadKeybindings()
    }

    /**
     * Restores tabs from the previous session if session restoration is enabled. It loads the preloaded session state,
     * restores each tab, and sets the active tab index.
     */
    private suspend fun restoreTabs() {
        SessionManager.mutex.withLock {
            val session = SessionManager.preloadedSession ?: return

            val deferredRestoredTabs =
                session.tabStates
                    .mapNotNull { tabState -> getTabFromState(tabState) }
                    .filter {
                        if (it is EditorTab) {
                            return@filter it.file.exists() && it.file.canRead()
                        }
                        true
                    }

            tabs.addAll(deferredRestoredTabs)

            currentTabIndex = session.currentTabIndex
        }
    }

    /**
     * Returns a restored tab instance from its serialized [TabState]. Used during session restoration.
     *
     * @param tabState The saved state of the tab to restore.
     * @return The restored [Tab], or `null` on failure.
     */
    private suspend fun getTabFromState(tabState: TabState): Tab? {
        return when (tabState) {
            is EditorTabState ->
                newEditorTab(editorState = tabState, checkDuplicate = false, switchToTab = false, openTab = false)

            is FileTabState -> TabRegistry.getTab(tabState.fileObject)
        }
    }

    /**
     * Opens a file in a new tab, or focuses it if already open.
     *
     * Before opening large files, it warns the user about potential memory issues.
     *
     * @param fileObject The file to open.
     * @param checkDuplicate If `true`, focus an existing tab for the file instead of opening a new one.
     * @param switchToTab If `true`, make the new or existing tab the active one.
     */
    suspend fun newTab(fileObject: FileObject, checkDuplicate: Boolean = true, switchToTab: Boolean = false) =
        withContext(Dispatchers.IO) {
            val function = suspend {
                val tab = TabRegistry.getTab(fileObject)
                if (tab == null) {
                    newEditorTab(file = fileObject, checkDuplicate = checkDuplicate, switchToTab = switchToTab)
                } else {
                    openTab(tab = tab, switchToTab = switchToTab)
                }
            }

            val coroutineScope = this
            if (Settings.oom_prediction && expectOOM(fileObject.length())) {
                dialog(
                    title = strings.attention.getString(),
                    msg = strings.tab_memory_warning.getString(),
                    okString = strings.continue_action,
                    onOk = { coroutineScope.launch { function.invoke() } },
                )
            } else {
                function.invoke()
            }
        }

    fun moveTab(from: Int, to: Int) {
        if (from == to || from !in tabs.indices || to !in tabs.indices) return

        val item = tabs.removeAt(from)
        tabs.add(to, item)

        // Update current index
        currentTabIndex =
            when (currentTabIndex) {
                from -> to
                in (minOf(from, to)..maxOf(from, to)) -> {
                    if (from < to) currentTabIndex - 1 else currentTabIndex + 1
                }
                else -> currentTabIndex
            }
    }

    /**
     * Checks if a tab for the given [file] is already open.
     *
     * @param file The file to check.
     * @return `true` if the tab is open, `false` otherwise.
     */
    suspend fun isEditorTabOpened(file: FileObject): Boolean =
        withContext(Dispatchers.IO) {
            tabs.toList().forEach { tab ->
                if (tab is EditorTab && tab.file == file) {
                    return@withContext true
                }
            }
            return@withContext false
        }

    /**
     * Creates a new editor tab from a saved state, used for session restoration. It restores the content, cursor
     * position, and scroll state.
     *
     * @param editorState The state object to restore.
     * @param checkDuplicate If `true`, avoids creating a duplicate tab for the same file.
     * @param switchToTab If `true`, makes the new tab active.
     * @param openTab If `false`, the tab is created but not added to the open tabs list.
     * @return The restored [Tab].
     */
    private suspend fun newEditorTab(
        editorState: EditorTabState,
        checkDuplicate: Boolean = true,
        switchToTab: Boolean = false,
        openTab: Boolean = true,
    ): Tab {
        val editorTab =
            newEditorTab(
                file = editorState.fileObject,
                checkDuplicate = checkDuplicate,
                switchToTab = switchToTab,
                openTab = openTab,
            )

        viewModelScope.launch {
            editorTab.editorState.contentRendered.await()
            val editor = editorTab.editorState.editor.get()!!
            editorState.unsavedContent?.let {
                editorTab.editorState.isDirty = true
                editor.setText(it)
            }

            val maxLine = editor.text.lineCount - 1
            val lineLeft = editorState.cursor.lineLeft.coerceAtMost(maxLine)
            val lineRight = editorState.cursor.lineRight.coerceAtMost(maxLine)

            val maxColumnLeft = editor.text.getColumnCount(lineLeft)
            val maxColumnRight = editor.text.getColumnCount(lineRight)
            val columnLeft = editorState.cursor.columnLeft.coerceAtMost(maxColumnLeft)
            val columnRight = editorState.cursor.columnRight.coerceAtMost(maxColumnRight)

            editor.setSelectionRegion(lineLeft, columnLeft, lineRight, columnRight)
            editor.scroller.startScroll(editorState.scrollX, editorState.scrollY, 0, 0)
        }

        return editorTab
    }

    /**
     * Creates and adds a new editor tab for the given file.
     *
     * @param file The file to open.
     * @param checkDuplicate If true, switches to an existing tab for this file.
     * @param switchToTab If true, makes the new tab active.
     * @param openTab If true, adds the tab to the list of open tabs.
     * @return The created or existing [EditorTab].
     */
    private suspend fun newEditorTab(
        file: FileObject,
        checkDuplicate: Boolean = true,
        switchToTab: Boolean = false,
        openTab: Boolean = true,
    ): EditorTab =
        withContext(Dispatchers.IO) {
            if (checkDuplicate) {
                tabs.forEachIndexed { index, tab ->
                    if (tab is EditorTab && tab.file == file) {
                        currentTabIndex = index
                        return@withContext tab
                    }
                }
            }

            return@withContext withContext(Dispatchers.Main) {
                mutex.withLock {
                    val editorTab = EditorTab(file = file, viewModel = this@MainViewModel)

                    if (openTab) tabs.add(editorTab)
                    if (openTab && switchToTab) {
                        currentTabIndex = tabs.lastIndex
                    }

                    editorTab
                }
            }
        }

    /**
     * Adds a pre-existing [Tab] to the list of open tabs.
     *
     * @param tab The tab to add.
     * @param switchToTab If `true`, makes the new tab active.
     */
    suspend fun openTab(tab: Tab, switchToTab: Boolean = false) {
        mutex.withLock {
            tabs.add(tab)
            if (switchToTab) {
                currentTabIndex = tabs.lastIndex
            }
        }
    }

    /**
     * Removes the tab at the specified [index].
     *
     * @param index The index of the tab to remove.
     * @return `true` if removed, `false` if the index was invalid.
     */
    fun removeTab(index: Int): Boolean {
        if (index !in tabs.indices) return false

        tabs[index].onTabRemoved()
        tabs.removeAt(index)

        currentTabIndex =
            when {
                tabs.isEmpty() -> 0
                index <= currentTabIndex -> maxOf(0, currentTabIndex - 1)
                else -> currentTabIndex
            }
        return true
    }

    /**
     * Remove all tabs except the current one
     *
     * @return true if any tabs were removed, false if no current tab or only one tab exists
     */
    fun removeOtherTabs(): Boolean {
        if (tabs.isEmpty() || currentTabIndex < 0 || currentTabIndex >= tabs.size) {
            return false
        }

        if (tabs.size <= 1) {
            return false
        }

        val currentTab = tabs[currentTabIndex]

        tabs.forEach {
            if (it != currentTab) {
                it.onTabRemoved()
            }
        }
        tabs.clear()
        tabs.add(currentTab)
        currentTabIndex = 0

        return true
    }

    /**
     * Close all tabs
     *
     * @return true if any tabs were closed, false if no tabs existed
     */
    fun removeAllTabs(): Boolean {
        if (tabs.isEmpty()) {
            return false
        }

        tabs.forEach { it.onTabRemoved() }

        tabs.clear()
        currentTabIndex = 0

        return true
    }

    /** Get the total number of tabs */
    fun getTabCount(): Int = tabs.size

    /** Check if there are any tabs open */
    fun hasOpenTabs(): Boolean = tabs.isNotEmpty()

    /**
     * Safely set the current tab index
     *
     * @param index the new index to set
     * @return true if the index was valid and set, false otherwise
     */
    fun setCurrentTabIndex(index: Int): Boolean {
        if (index < 0 || index >= tabs.size) {
            return false
        }
        currentTabIndex = index
        return true
    }

    /** Go to or open tab that contains the range and select it. */
    suspend fun goToTabAndSelect(file: FileObject, lineStart: Int, charStart: Int, lineEnd: Int, charEnd: Int) {
        withContext(Dispatchers.Main) { newTab(file, switchToTab = true) }

        val targetTab = tabs.filterIsInstance<EditorTab>().find { it.file == file }

        // Wait until editor content is loaded
        targetTab!!.editorState.contentRendered.await()

        withContext(Dispatchers.Main) {
            targetTab.editorState.editor
                .get()
                ?.setSelectionRegion(lineStart, charStart, lineEnd, charEnd, SelectionChangeEvent.CAUSE_SEARCH)

            targetTab.editorState.editor.get()?.ensureSelectionVisible()
        }
    }
}
