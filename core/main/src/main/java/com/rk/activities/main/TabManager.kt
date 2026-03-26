package com.rk.activities.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.rk.tabs.base.Tab

class TabManager {
    private val _tabs = mutableStateListOf<Tab>()
    val tabs: List<Tab>
        get() = _tabs.toList()

    var currentTabIndex by mutableIntStateOf(0)
        private set

    val currentTab: Tab?
        get() = _tabs.getOrNull(currentTabIndex)

    fun addTab(tab: Tab, switchToTab: Boolean, checkDuplicate: Boolean = true) {
        val duplicateIndex = if (checkDuplicate) _tabs.indexOfFirst { it.file == tab.file } else -1

        if (duplicateIndex != -1) {
            if (switchToTab) setCurrentTab(duplicateIndex)
            return
        }

        _tabs.add(tab)
        tab.onTabAdded()

        if (switchToTab) {
            setCurrentTab(_tabs.lastIndex)
        }
    }

    fun removeTab(index: Int) {
        if (index !in _tabs.indices) return

        val removingTab = _tabs[index]
        val wasCurrent = index == currentTabIndex

        val newIndex = when {
            _tabs.size == 1 -> 0
            index < currentTabIndex -> currentTabIndex - 1
            index > currentTabIndex -> currentTabIndex
            else -> maxOf(0, currentTabIndex - 1)
        }

        _tabs.removeAt(index)
        removingTab.onTabRemoved()
        currentTabIndex = newIndex

        if (wasCurrent && _tabs.isNotEmpty()) {
            _tabs[currentTabIndex].onTabSelected()
        }
    }

    fun removeTab(tab: Tab) = removeTab(_tabs.indexOf(tab))

    fun moveTab(from: Int, to: Int) {
        if (from == to || from !in _tabs.indices || to !in _tabs.indices) return

        val item = _tabs.removeAt(from)
        _tabs.add(to, item)

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
    }

    fun removeOtherTabs() {
        val tabToKeep = currentTab ?: return

        _tabs.forEach { if (it != tabToKeep) it.onTabRemoved() }
        _tabs.removeAll { it != tabToKeep }
        currentTabIndex = 0
    }

    fun removeAllTabs() {
        _tabs.forEach { it.onTabRemoved() }
        _tabs.clear()
        currentTabIndex = 0
    }
}
