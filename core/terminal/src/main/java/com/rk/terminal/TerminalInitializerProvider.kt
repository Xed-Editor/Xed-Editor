package com.rk.terminal

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.rk.activities.terminal.Terminal
import com.rk.activities.terminal.TerminalNavigation
import com.rk.settings.SettingsRegistry
import com.rk.settings.terminal.SettingsTerminalScreen
import com.rk.settings.terminal.TerminalCheckScreen
import com.rk.settings.terminal.TerminalExtraKeys
import com.rk.settings.editor.TerminalFontScreen

class TerminalInitializerProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        // Register terminal screens using the general-purpose registry
        SettingsRegistry.registerScreen(com.rk.activities.settings.SettingsRoutes.TerminalSettings.route) { navController ->
            SettingsTerminalScreen(navController)
        }
        SettingsRegistry.registerScreen(com.rk.activities.settings.SettingsRoutes.TerminalFontScreen.route) {
            TerminalFontScreen()
        }
        SettingsRegistry.registerScreen(com.rk.activities.settings.SettingsRoutes.TerminalExtraKeys.route) {
            TerminalExtraKeys()
        }
        SettingsRegistry.registerScreen(com.rk.activities.settings.SettingsRoutes.TerminalCheck.route) {
            TerminalCheckScreen()
        }

        // Register the Terminal class for navigation
        TerminalNavigation.setTerminalClass(Terminal::class.java)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
