package com.rk.drawer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.file.FileObject
import com.rk.filetree.FileTreeTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DrawerViewModel : ViewModel() {
    var isLoading by mutableStateOf(true)

    private val _drawerTabs = mutableStateListOf<DrawerTab>()
    private val _serviceTabs = mutableStateListOf<DrawerTab>()

    val drawerTabs: List<DrawerTab>
        get() = _drawerTabs.toList()

    val serviceTabs: List<DrawerTab>
        get() = _serviceTabs.toList()

    var currentDrawerTabIndex by mutableIntStateOf(0)
        private set

    val currentDrawerTab: DrawerTab?
        get() = _drawerTabs.getOrNull(currentDrawerTabIndex)

    var currentServiceTabIndex by mutableIntStateOf(0)
        private set

    val currentServiceTab: DrawerTab?
        get() = _serviceTabs.getOrNull(currentServiceTabIndex)

    internal fun setupBuiltinServices(owner: androidx.lifecycle.ViewModelStoreOwner) {
        _serviceTabs.clear()
        ServiceTabRegistry.providers.forEach { provider ->
            _serviceTabs.add(provider(owner))
        }
        currentServiceTabIndex = -1
    }

    fun addFileTreeTab(fileObject: FileObject, save: Boolean = false) {
        val existingIndex = _drawerTabs.indexOfFirst { it is FileTreeTab && it.root == fileObject }

        if (existingIndex != -1) {
            selectDrawerTab(existingIndex)
            return
        }

        val tab = FileTreeTab(fileObject)
        addDrawerTab(tab, save)
    }

    fun addDrawerTab(tab: DrawerTab, save: Boolean = false) {
        tab.onAdded()

        _drawerTabs.add(tab)
        selectDrawerTab(_drawerTabs.lastIndex)

        if (save) persistAsync()
    }

    fun removeFileTreeTab(fileObject: FileObject, save: Boolean = false) {
        val index = _drawerTabs.indexOfFirst { it is FileTreeTab && it.root == fileObject }
        if (index == -1) return

        removeDrawerTab(index, save)
    }

    fun removeDrawerTab(drawerTab: DrawerTab, save: Boolean = false) {
        val index = _drawerTabs.indexOf(drawerTab)
        if (index == -1) return

        removeDrawerTab(index, save)
    }

    fun removeDrawerTab(index: Int, save: Boolean = false) {
        if (index !in _drawerTabs.indices) return

        val isActive = currentDrawerTabIndex == index

        _drawerTabs[index].onRemoved()
        _drawerTabs.removeAt(index)

        if (_drawerTabs.isEmpty()) {
            unselectDrawerTab()
        } else if (isActive) {
            val newIndex =
                when {
                    index - 1 >= 0 -> index - 1
                    index <= _drawerTabs.lastIndex -> index
                    else -> _drawerTabs.lastIndex
                }
            selectDrawerTab(newIndex)
        } else {
            if (currentDrawerTabIndex > index) {
                currentDrawerTabIndex -= 1
            }
        }

        if (save) persistAsync()
    }

    fun selectDrawerTab(drawerTab: DrawerTab) {
        val index = _drawerTabs.indexOf(drawerTab)
        if (index != -1) selectDrawerTab(index)
    }

    fun selectDrawerTab(index: Int) {
        if (index !in _drawerTabs.indices) return

        currentDrawerTabIndex = index
        currentServiceTabIndex = -1
    }

    fun unselectDrawerTab() {
        currentDrawerTabIndex = -1
        currentServiceTabIndex = -1
    }

    fun selectServiceTab(serviceTab: DrawerTab) {
        val index = _serviceTabs.indexOf(serviceTab)
        if (index != -1) selectServiceTab(index)
    }

    fun selectServiceTab(index: Int) {
        if (index !in _serviceTabs.indices) return

        currentServiceTabIndex = index
    }

    fun unselectServiceTab() {
        currentServiceTabIndex = -1
    }

    fun forcePushDrawerTabs(drawerTabs: List<DrawerTab>) {
        _drawerTabs.clear()
        _drawerTabs.addAll(drawerTabs)
    }

    private fun persistAsync() {
        viewModelScope.launch(Dispatchers.IO) { DrawerPersistence.saveState(this@DrawerViewModel) }
    }
}
