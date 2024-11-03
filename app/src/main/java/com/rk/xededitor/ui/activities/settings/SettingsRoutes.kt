package com.rk.xededitor.ui.activities.settings

sealed class SettingsRoutes(val route: String) {
    data object Settings : SettingsRoutes("settings")

    data object AppSettings : SettingsRoutes("app_settings")

    data object EditorSettings : SettingsRoutes("editor_settings")

    data object TerminalSettings : SettingsRoutes("terminal_settings")

    data object GitSettings : SettingsRoutes("git_settings")

    data object About : SettingsRoutes("karbon")
}
