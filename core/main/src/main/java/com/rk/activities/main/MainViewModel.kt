package com.rk.activities.main

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.commands.Command
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.utils.application
import com.rk.utils.dialog
import com.rk.utils.expectOOM
import com.rk.utils.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.EditorTab
import com.rk.tabs.Tab
import com.rk.tabs.TabRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object TabCache {
    val mutex = Mutex()
    val preloadedTabStates = mutableListOf<TabState>()
    suspend fun preloadTabStates() = mutex.withLock {
        runCatching {
            val tabCacheFile = application!!.cacheDir.child("tabs")
            if (tabCacheFile.exists() && tabCacheFile.canRead()) {
                ObjectInputStream(FileInputStream(tabCacheFile)).use { ois ->
                    preloadedTabStates.clear()

                    val obj = ois.readObject()
                    if (obj is List<*>) {
                        val tabStates = obj.filterIsInstance<TabState>()
                        preloadedTabStates.addAll(tabStates)
                    }
                }
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    suspend fun saveFileTabs(tabs: List<Tab>) = withContext(Dispatchers.IO){
        mutex.withLock {
            runCatching {
                val tabStates = tabs.mapNotNull { it.getState() }
                val tabCacheFile = application!!.cacheDir.child("tabs")

                ObjectOutputStream(FileOutputStream(tabCacheFile)).use { oos ->
                    oos.writeObject(tabStates)
                }
            }.onFailure {
                it.printStackTrace()
                toast("Unable to save tabs")
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
    var commands = emptyList<Command>()

    val currentTab: Tab?
        get() = tabs.getOrNull(currentTabIndex)

    init {
        if (Settings.restore_sessions) {
            restoreTabs()
        }
    }

    private fun restoreTabs() {
        viewModelScope.launch {
            TabCache.mutex.withLock {
                TabCache.preloadedTabStates.forEach { tabState ->
                    restoreTabFromState(tabState)
                }
            }
        }
    }

    private fun restoreTabFromState(tabState: TabState) {
        viewModelScope.launch {
            when (tabState) {
                is EditorTabState -> {
                    newEditorTab(
                        editorState = tabState,
                        checkDuplicate = false,
                        switchToTab = false
                    )
                }
                is FileTabState -> {
                    newTab(
                        fileObject = tabState.fileObject,
                        checkDuplicate = false,
                        switchToTab = false
                    )
                }
            }
        }
    }

    suspend fun newTab(
        fileObject: FileObject,
        checkDuplicate: Boolean = true,
        switchToTab: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val function = suspend {
            TabRegistry.getTab(fileObject) {
                if (it == null) {
                    newEditorTab(
                        file = fileObject,
                        checkDuplicate = checkDuplicate,
                        switchToTab = switchToTab
                    )
                } else {
                    newTab(it)
                }
            }
        }

        val coroutineScope = this
        if (expectOOM(fileObject.length())) {
            dialog(
                title = strings.attention.getString(),
                msg = strings.tab_memory_warning.getString(),
                okString = strings.continue_action,
                onOk = {
                    coroutineScope.launch {
                        function.invoke()
                    }
                })
        } else {
            function.invoke()
        }
    }

    suspend fun isEditorTabOpened(file: FileObject): Boolean = withContext(Dispatchers.IO){
        tabs.toList().forEachIndexed { index, tab ->
            if (tab is EditorTab && tab.file == file){
                return@withContext true
            }
        }
        return@withContext false
    }

    private suspend fun newEditorTab(
        editorState: EditorTabState,
        checkDuplicate: Boolean = true,
        switchToTab: Boolean = false
    ) {
        val editorTab = newEditorTab(
            file = editorState.fileObject,
            checkDuplicate = checkDuplicate,
            switchToTab = switchToTab
        )
        editorTab.editorState.contentRendered.await()
        val editor = editorTab.editorState.editor.get()!!
        editorState.unsavedContent?.let {
            editor.setText(it)
        }
        editor.setSelectionRegion(
            editorState.cursor.lineLeft,
            editorState.cursor.columnLeft,
            editorState.cursor.lineRight,
            editorState.cursor.columnRight
        )
        editor.scrollX = editorState.scrollX
        editor.scrollY = editorState.scrollY
    }

    private suspend fun newEditorTab(
        file: FileObject,
        checkDuplicate: Boolean = true,
        switchToTab: Boolean = false
    ): EditorTab = withContext(
        Dispatchers.IO
    ) {
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

                tabs.add(editorTab)
                if (switchToTab) {
                    delay(70)
                    currentTabIndex = tabs.lastIndex
                }

                editorTab
            }
        }
    }

    suspend fun newTab(tab: Tab){
        mutex.withLock{
            tabs.add(tab)
            delay(70)
            currentTabIndex = tabs.lastIndex
        }
    }

    fun removeTab(index: Int): Boolean {
        if (index !in tabs.indices) return false

        (tabs[index] as? EditorTab)?.onTabRemoved()

        tabs.removeAt(index)

        currentTabIndex = when {
            tabs.isEmpty() -> 0
            index <= currentTabIndex -> maxOf(0, currentTabIndex - 1)
            else -> currentTabIndex
        }
        return true
    }

    /**
     * Remove all tabs except the current one
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

        tabs.clear()
        tabs.add(currentTab)
        currentTabIndex = 0

        return true
    }

    /**
     * Close all tabs
     * @return true if any tabs were closed, false if no tabs existed
     */
    fun closeAllTabs(): Boolean {
        if (tabs.isEmpty()) {
            return false
        }

        tabs.clear()
        currentTabIndex = 0

        return true
    }

    /**
     * Get the total number of tabs
     */
    fun getTabCount(): Int = tabs.size

    /**
     * Check if there are any tabs open
     */
    fun hasOpenTabs(): Boolean = tabs.isNotEmpty()

    /**
     * Safely set the current tab index
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
}