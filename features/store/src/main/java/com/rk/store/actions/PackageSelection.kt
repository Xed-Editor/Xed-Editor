package com.rk.store.actions

import com.rk.App.Companion.iconPackManager
import com.rk.App.Companion.themeManager
import com.rk.DefaultScope
import com.rk.events.AppEvent
import com.rk.events.Events
import com.rk.icons.pack.currentIconPack
import com.rk.settings.Settings
import com.rk.settings.editor.refreshEditors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Activates the freshly installed theme and notifies the rest of the app. */
suspend fun applyThemeAfterInstall(id: String) =
    withContext(Dispatchers.Main) {
        val oldTheme = themeManager.currentTheme
        Settings.theme = id
        refreshEditors()
        DefaultScope.launch { Events.publish(AppEvent.ThemeChanged(themeManager.currentTheme, oldTheme)) }
    }

/** Activates the freshly installed icon pack and notifies the rest of the app. */
suspend fun applyIconPackAfterInstall(id: String) =
    withContext(Dispatchers.Main) {
        val oldIconPack = currentIconPack.value
        val newIconPack = iconPackManager.localIconPacks.value[id]
        currentIconPack.value = newIconPack
        Settings.icon_pack = id
        DefaultScope.launch { Events.publish(AppEvent.IconPackChanged(newIconPack, oldIconPack)) }
    }
