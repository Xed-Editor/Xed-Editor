package com.rk.activities.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rk.animations.NavigationAnimationTransitions
import com.rk.settings.SettingsScreen
import com.rk.settings.about.AboutScreen
import com.rk.settings.app.SettingsAppScreen
import com.rk.settings.developer_options.DeveloperOptions
import com.rk.settings.editor.DefaultEncoding
import com.rk.settings.editor.EditorFontScreen
import com.rk.settings.lsp.LspSettings
import com.rk.settings.editor.SettingsEditorScreen
import com.rk.settings.editor.ToolbarActions
import com.rk.settings.language.LanguageScreen
import com.rk.settings.mutators.ManageMutators
import com.rk.settings.runners.Runners
import com.rk.settings.support.Support
import com.rk.settings.terminal.SettingsTerminalScreen
import com.rk.settings.theme.ThemeScreen

@Composable
fun SettingsNavHost(navController: NavHostController, activity: SettingsActivity) {
    NavHost(
        navController = navController,
        startDestination = SettingsRoutes.Settings.route,
        enterTransition = { NavigationAnimationTransitions.enterTransition },
        exitTransition = { NavigationAnimationTransitions.exitTransition },
        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition },
        popExitTransition = { NavigationAnimationTransitions.popExitTransition },
    ) {

        composable(SettingsRoutes.Settings.route) { SettingsScreen(navController) }
        composable(SettingsRoutes.AppSettings.route) { SettingsAppScreen(activity, navController) }
        composable(SettingsRoutes.EditorSettings.route) { SettingsEditorScreen(navController) }
        composable(SettingsRoutes.TerminalSettings.route) { SettingsTerminalScreen() }
        composable(SettingsRoutes.About.route) { AboutScreen() }
        composable(SettingsRoutes.EditorFontScreen.route) { EditorFontScreen() }
        composable(SettingsRoutes.DefaultEncoding.route) { DefaultEncoding() }
        composable(SettingsRoutes.ToolbarActions.route) { ToolbarActions() }
        composable(SettingsRoutes.DeveloperOptions.route) { DeveloperOptions(navController = navController) }
        composable(SettingsRoutes.ManageMutators.route) { ManageMutators(navController = navController) }
        composable(SettingsRoutes.Support.route) { Support() }
        composable(SettingsRoutes.LanguageScreen.route) { LanguageScreen() }
        composable(SettingsRoutes.Runners.route) { Runners() }
        composable(SettingsRoutes.LspSettings.route) { LspSettings() }
        composable(SettingsRoutes.Themes.route) { ThemeScreen() }
    }
}