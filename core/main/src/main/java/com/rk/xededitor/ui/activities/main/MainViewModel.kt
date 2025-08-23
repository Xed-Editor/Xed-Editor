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
import com.rk.settings.Settings
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
    val mutex = Mutex()
    var currentTabIndex by mutableIntStateOf(0)

    val currentTab: Tab?
        get() = tabs.getOrNull(currentTabIndex)


    init {
        if (Settings.restore_sessions){
            viewModelScope.launch{
                TabCache.mutex.withLock{
                    TabCache.preloadedTabs.forEach { file ->
                        viewModelScope.launch {
                            newEditorTab(file,checkDuplicate = false,switchToTab = false)
                        }
                    }
                }
            }
        }
    }

    suspend fun newEditorTab(file: FileObject, checkDuplicate: Boolean = true,switchToTab: Boolean = false): Boolean = withContext(
        Dispatchers.IO) {
        if (checkDuplicate && tabs.any { it is EditorTab && it.file == file }) {
            return@withContext false
        }

        return@withContext withContext(Dispatchers.Main) {
                mutex.withLock{
                    val editorTab = EditorTab(file = file, viewModel = this@MainViewModel)

                    tabs.add(editorTab)
                    if (switchToTab){
                        delay(70)
                        currentTabIndex = tabs.lastIndex
                    }
                }

                true
            }

    }

    fun removeTab(index: Int): Boolean {
        if (index !in tabs.indices) return false

        // Close tab resources if needed
        (tabs[index] as? EditorTab)?.onTabRemoved()

        tabs.removeAt(index)

        // More robust index adjustment
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