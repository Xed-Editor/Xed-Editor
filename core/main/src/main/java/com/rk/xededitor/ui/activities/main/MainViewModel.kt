package com.rk.xededitor.ui.activities.main

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.rk.file.FileObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    private var nextTabId = 0
    val tabs = mutableStateListOf<Tab>()
    var currentTabIndex by mutableIntStateOf(0)

    val currentTab: Tab? get() {
        return tabs.getOrNull(currentTabIndex)
    }

    // Editor tab management
    suspend fun newEditorTab(file: FileObject): Boolean {
        withContext(Dispatchers.IO){
            if (tabs.any { it is EditorTab && it.file == file }){
                return@withContext false
            }
        }
        tabs.add(EditorTab(viewPagerId = nextTabId++, file = file, viewModel = this))
        currentTabIndex = tabs.lastIndex
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