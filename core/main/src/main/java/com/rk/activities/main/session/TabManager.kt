package com.rk.activities.main.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.events.EditorTabEvent
import com.rk.events.Events
import com.rk.events.TabEvent
import com.rk.filetree.FileTreeTab
import com.rk.tabs.base.Tab
import com.rk.tabs.editor.EditorTab
import kotlinx.coroutines.launch

class TabManager {
    private val _tabs = mutableStateListOf<Tab>()
    val tabs: List<Tab>
        get() = _tabs.toList()

    private val selectionHistory = mutableListOf<Tab>()

    var currentTabIndex by mutableIntStateOf(0)
        private set

    val currentTab: Tab?
        get() = _tabs.getOrNull(currentTabIndex)

    fun addTab(tab: Tab, switchToTab: Boolean, checkDuplicate: Boolean = true) {
        if (tab.scopeRoot == null) {
            val drawerViewModel = MainActivity.instance?.drawerViewModel
            val currentFileTreeTab = drawerViewModel?.currentDrawerTab as? FileTreeTab
            tab.scopeRoot = currentFileTreeTab?.root
        }

        val duplicateIndex =
            if (checkDuplicate) {
                _tabs.indexOfFirst { it == tab }
            } else -1

        if (duplicateIndex != -1) {
            val existingTab = _tabs[duplicateIndex]
            existingTab.onDuplicate(tab)
            if (switchToTab) setCurrentTab(duplicateIndex)
            return
        }

        _tabs.add(tab)
        selectionHistory.add(0, tab)
        tab.onTabAdded()

        DefaultScope.launch {
            Events.publish(TabEvent.Opened(tab))
            if (tab is EditorTab) {
                Events.publish(EditorTabEvent.Opened(tab))
            }
        }

        if (switchToTab) {
            setCurrentTab(_tabs.lastIndex)
        }
    }

    fun removeTab(index: Int) {
        if (index !in _tabs.indices) return

        val tab = _tabs[index]
        tab.onTabRemoved()
        _tabs.removeAt(index)
        selectionHistory.remove(tab)

        DefaultScope.launch {
            Events.publish(TabEvent.Closed(tab))
            if (tab is EditorTab) {
                Events.publish(EditorTabEvent.Closed(tab))
            }
        }

        if (_tabs.isEmpty()) {
            currentTabIndex = 0
        } else if (index == currentTabIndex) {
            val nextTab = selectionHistory.firstOrNull()
            val nextIndex = if (nextTab != null) _tabs.indexOf(nextTab) else -1

            if (nextIndex != -1) {
                setCurrentTab(nextIndex)
            } else {
                val newIndex = maxOf(0, index - 1)
                setCurrentTab(newIndex)
            }
        } else if (index < currentTabIndex) {
            currentTabIndex--
        }
    }

    fun removeTab(tab: Tab) = removeTab(_tabs.indexOf(tab))

    fun moveTab(from: Int, to: Int) {
        if (from == to || from !in _tabs.indices || to !in _tabs.indices) return

        val item = _tabs.removeAt(from)
        _tabs.add(to, item)

        DefaultScope.launch {
            Events.publish(TabEvent.Reordered(item, from, to))
            if (item is EditorTab) {
                Events.publish(EditorTabEvent.Reordered(item, from, to))
            }
        }

        setCurrentTab(
            when (currentTabIndex) {
                from -> to
                in (minOf(from, to)..maxOf(from, to)) -> {
                    if (from < to) currentTabIndex - 1 else currentTabIndex + 1
                }
                else -> currentTabIndex
            }
        )
    }

    fun setCurrentTab(index: Int) {
        if (index !in _tabs.indices) return
        if (index == currentTabIndex) return

        currentTab?.onTabUnselected()
        currentTabIndex = index
        currentTab?.onTabSelected()

        val tab = currentTab ?: return
        selectionHistory.remove(tab)
        selectionHistory.add(0, tab)

        DefaultScope.launch {
            Events.publish(TabEvent.Selected(tab))
            if (tab is EditorTab) {
                Events.publish(EditorTabEvent.Selected(tab))
            }
        }
    }

    fun removeOtherTabs() {
        val tabToKeep = currentTab ?: return

        _tabs.forEach { if (it != tabToKeep) it.onTabRemoved() }
        _tabs.removeAll { it != tabToKeep }
        selectionHistory.clear()
        selectionHistory.add(tabToKeep)
        currentTabIndex = 0
    }

    fun removeAllTabs() {
        _tabs.forEach { it.onTabRemoved() }
        _tabs.clear()
        selectionHistory.clear()
        currentTabIndex = 0
    }
}
