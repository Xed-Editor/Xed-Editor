package com.rk.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.rk.events.DrawerEvent
import com.rk.events.Events
import com.rk.file.FileObject
import com.rk.filetree.FileTreeTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DrawerViewModel : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _drawerTabs = MutableStateFlow<List<DrawerTab>>(emptyList())
    val drawerTabs = _drawerTabs.asStateFlow()

    private val _serviceTabs = MutableStateFlow<List<DrawerTab>>(emptyList())
    val serviceTabs = _serviceTabs.asStateFlow()

    private val _currentDrawerTabIndex = MutableStateFlow(0)
    val currentDrawerTabIndex = _currentDrawerTabIndex.asStateFlow()

    val currentDrawerTab: DrawerTab?
        get() = _drawerTabs.value.getOrNull(_currentDrawerTabIndex.value)

    private val _currentServiceTabIndex = MutableStateFlow(0)
    val currentServiceTabIndex = _currentServiceTabIndex.asStateFlow()

    val currentServiceTab: DrawerTab?
        get() = _serviceTabs.value.getOrNull(_currentServiceTabIndex.value)

    fun setLoading(value: Boolean) {
        _isLoading.value = value
    }

    internal fun setupBuiltinServices(owner: ViewModelStoreOwner) {
        _serviceTabs.value = ServiceTabRegistry.createAll(owner)
        _currentServiceTabIndex.value = -1
        viewModelScope.launch { Events.publish(DrawerEvent.ServicesInitialized(_serviceTabs.value)) }
    }

    fun addFileTreeTab(fileObject: FileObject, save: Boolean = false) {
        val existingIndex = _drawerTabs.value.indexOfFirst { it is FileTreeTab && it.root == fileObject }

        if (existingIndex != -1) {
            selectDrawerTab(existingIndex)
            return
        }

        val tab = FileTreeTab(fileObject)
        addDrawerTab(tab, save)
    }

    fun addDrawerTab(tab: DrawerTab, save: Boolean = false) {
        tab.onAdded()

        _drawerTabs.update { it + tab }
        selectDrawerTab(_drawerTabs.value.lastIndex)

        viewModelScope.launch { Events.publish(DrawerEvent.TabAdded(tab)) }

        if (save) persistAsync()
    }

    fun removeFileTreeTab(fileObject: FileObject, save: Boolean = false) {
        val index = _drawerTabs.value.indexOfFirst { it is FileTreeTab && it.root == fileObject }
        if (index == -1) return

        removeDrawerTab(index, save)
    }

    fun removeDrawerTab(drawerTab: DrawerTab, save: Boolean = false) {
        val index = _drawerTabs.value.indexOf(drawerTab)
        if (index == -1) return

        removeDrawerTab(index, save)
    }

    fun removeDrawerTab(index: Int, save: Boolean = false) {
        val tabs = _drawerTabs.value
        if (index !in tabs.indices) return

        val isActive = _currentDrawerTabIndex.value == index

        val tab = tabs[index]
        tab.onRemoved()
        _drawerTabs.update { it.filterIndexed { i, _ -> i != index } }

        viewModelScope.launch { Events.publish(DrawerEvent.TabRemoved(tab)) }

        if (_drawerTabs.value.isEmpty()) {
            unselectDrawerTab()
        } else if (isActive) {
            val newIndex =
                when {
                    index - 1 >= 0 -> index - 1
                    index <= _drawerTabs.value.lastIndex -> index
                    else -> _drawerTabs.value.lastIndex
                }
            selectDrawerTab(newIndex)
        } else {
            if (_currentDrawerTabIndex.value > index) {
                _currentDrawerTabIndex.value -= 1
            }
        }

        if (save) persistAsync()
    }

    fun selectDrawerTab(drawerTab: DrawerTab) {
        val index = _drawerTabs.value.indexOf(drawerTab)
        if (index != -1) selectDrawerTab(index)
    }

    fun selectDrawerTab(index: Int) {
        if (index !in _drawerTabs.value.indices) return

        _currentDrawerTabIndex.value = index
        _currentServiceTabIndex.value = -1

        viewModelScope.launch { Events.publish(DrawerEvent.TabSelected(currentDrawerTab)) }
    }

    fun moveDrawerTab(from: Int, to: Int) {
        val tabs = _drawerTabs.value.toMutableList()
        if (from !in tabs.indices || to !in tabs.indices || from == to) return

        val item = tabs.removeAt(from)
        tabs.add(to, item)
        _drawerTabs.value = tabs

        when (val currentIndex = _currentDrawerTabIndex.value) {
            from -> _currentDrawerTabIndex.value = to
            in (from + 1)..to -> _currentDrawerTabIndex.value = currentIndex - 1
            in to..<from -> _currentDrawerTabIndex.value = currentIndex + 1
        }

        persistAsync()
    }

    fun unselectDrawerTab() {
        _currentDrawerTabIndex.value = -1
        _currentServiceTabIndex.value = -1

        viewModelScope.launch { Events.publish(DrawerEvent.TabSelected(null)) }
    }

    fun selectServiceTab(serviceTab: DrawerTab) {
        val index = _serviceTabs.value.indexOf(serviceTab)
        if (index != -1) selectServiceTab(index)
    }

    fun selectServiceTab(index: Int) {
        if (index !in _serviceTabs.value.indices) return

        _currentServiceTabIndex.value = index

        viewModelScope.launch { Events.publish(DrawerEvent.ServiceTabSelected(currentServiceTab)) }
    }

    fun unselectServiceTab() {
        _currentServiceTabIndex.value = -1

        viewModelScope.launch { Events.publish(DrawerEvent.ServiceTabSelected(null)) }
    }

    fun forcePushDrawerTabs(drawerTabs: List<DrawerTab>) {
        _drawerTabs.value = drawerTabs
    }

    private fun persistAsync() {
        viewModelScope.launch(Dispatchers.IO) { DrawerPersistence.saveState(this@DrawerViewModel) }
    }
}
