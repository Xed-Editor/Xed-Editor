package com.rk.xededitor.ui.activities.settings

sealed class SettingsRoutes(val route: String) {
    object Settings : SettingsRoutes("settings")
    object AppSettings : SettingsRoutes("app_settings")
    object EditorSettings : SettingsRoutes("editor_settings")
    object PluginSettings : SettingsRoutes("plugin_settings")
    object TerminalSettings : SettingsRoutes("terminal_settings")
    object GitSettings : SettingsRoutes("git_settings")
}