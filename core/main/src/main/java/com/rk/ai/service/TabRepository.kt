package com.rk.ai.service

import com.rk.activities.main.TabManager
import com.rk.tabs.base.Tab

interface TabRepository {
    val tabs: List<Tab>
    val currentTab: Tab?
    val tabManager: TabManager
}
