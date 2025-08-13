package com.rk.xededitor.ui.activities.main

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.libcommons.application
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toast
import kotlinx.coroutines.Dispatchers
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
    val preloadedTabs = mutableListOf<FileObject>()



    suspend fun preloadTabs() = mutex.withLock{
        runCatching {
            val file = application!!.cacheDir.child("tabs")
            if (file.exists() && file.canRead()) {
                ObjectInputStream(FileInputStream(file)).use { ois ->
                    val files = ois.readObject() as List<FileObject>
                    preloadedTabs.clear()
                    preloadedTabs.addAll(files.filter {
                        it.exists() && it.canRead() && it.canWrite() && it.isFile()
                    })
                }
            }
        }.onFailure {
            errorDialog(it)
        }
    }

    suspend fun saveFileTabs(tabs: List<Tab>) = withContext(Dispatchers.IO){
        mutex.withLock {
            runCatching {
                val files = tabs.filter { it is EditorTab }.map { (it as EditorTab).file }

                val file = application!!.cacheDir.child("tabs")

                ObjectOutputStream(FileOutputStream(file)).use { oos ->
                    oos.writeObject(files)
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

    init {
        viewModelScope.launch{
            TabCache.mutex.withLock{
                TabCache.preloadedTabs.forEach { file ->
                    viewModelScope.launch {
                        newEditorTab(file)
                    }
                }
            }
        }

    }

    private var nextTabId = 0

    var currentTabIndex by mutableIntStateOf(0)

    val currentTab: Tab? get() {
        return tabs.getOrNull(currentTabIndex)
    }

    // Editor tab management
    suspend fun newEditorTab(file: FileObject, checkDuplicate:Boolean = true): Boolean {
        if (checkDuplicate){
            withContext(Dispatchers.IO){
                if (tabs.any { it is EditorTab && it.file == file }){
                    return@withContext false
                }
            }
        }

        withContext(Dispatchers.Main.immediate){
            val editorTab = EditorTab(viewPagerId = nextTabId++, file = file, viewModel = this@MainViewModel)
            tabs.add(editorTab)

            currentTabIndex = tabs.lastIndex
        }
        return true
    }

    /**
     * Remove the current tab
     * @return true if a tab was removed, false if no current tab exists
     */
    fun removeCurrentTab(): Boolean {
        if (tabs.isEmpty() || currentTabIndex < 0 || currentTabIndex >= tabs.size) {
            return false
        }

        tabs.removeAt(currentTabIndex)


        // Adjust currentTabIndex after removal
        when {
            tabs.isEmpty() -> currentTabIndex = 0
            currentTabIndex >= tabs.size -> currentTabIndex = tabs.lastIndex
            // If currentTabIndex is still valid, keep it as is
        }

        return true
    }

    /**
     * Remove a specific tab by index
     * @param index the index of the tab to remove
     * @return true if the tab was removed, false if index is invalid
     */
    fun removeTab(index: Int): Boolean {
        if (index < 0 || index >= tabs.size) {
            return false
        }

        tabs.removeAt(index)


        // Adjust currentTabIndex after removal
        when {
            tabs.isEmpty() -> currentTabIndex = 0
            currentTabIndex > index -> currentTabIndex--
            currentTabIndex >= tabs.size -> currentTabIndex = tabs.lastIndex
            // If currentTabIndex is still valid and not affected, keep it as is
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
            return false // No other tabs to remove
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