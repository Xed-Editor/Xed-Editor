package com.rk.activities.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rk.animations.NavigationAnimationTransitions
import com.rk.settings.SettingsScreen
import com.rk.settings.about.AboutScreen
import com.rk.settings.app.SettingsAppScreen
import com.rk.settings.debugOptions.DeveloperOptions
import com.rk.settings.editor.DefaultEncoding
import com.rk.settings.editor.DefaultLineEnding
import com.rk.settings.editor.EditExtraKeys
import com.rk.settings.editor.EditToolbarActions
import com.rk.settings.editor.EditorFontScreen
import com.rk.settings.editor.ExcludeFiles
import com.rk.settings.editor.SettingsEditorScreen
import com.rk.settings.extension.Extensions
import com.rk.settings.git.GitSettings
import com.rk.settings.keybinds.KeybindingsScreen
import com.rk.settings.language.LanguageScreen
import com.rk.settings.lsp.LspSettings
import com.rk.settings.runners.HtmlRunnerSettings
import com.rk.settings.runners.RunnerSettings
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
        composable(SettingsRoutes.Keybindings.route) { KeybindingsScreen() }
        composable(SettingsRoutes.TerminalSettings.route) { SettingsTerminalScreen() }
        composable(SettingsRoutes.About.route) { AboutScreen() }
        composable(SettingsRoutes.EditorFontScreen.route) { EditorFontScreen() }
        composable(SettingsRoutes.DefaultEncoding.route) { DefaultEncoding() }
        composable(SettingsRoutes.DefaultLineEnding.route) { DefaultLineEnding() }
        composable(SettingsRoutes.ToolbarActions.route) { EditToolbarActions() }
        composable(SettingsRoutes.ExtraKeys.route) { EditExtraKeys() }
        composable(
            "${SettingsRoutes.ExcludeFiles.route}/{isDrawer}",
            arguments = listOf(navArgument("isDrawer", builder = { type = NavType.BoolType })),
        ) {
            val isDrawer = it.arguments?.getBoolean("isDrawer")!!
            ExcludeFiles(isDrawer)
        }
        composable(SettingsRoutes.DeveloperOptions.route) { DeveloperOptions(navController = navController) }
        composable(SettingsRoutes.Support.route) { Support() }
        composable(SettingsRoutes.LanguageScreen.route) { LanguageScreen() }
        composable(SettingsRoutes.Runners.route) { RunnerSettings(navController = navController) }
        composable(SettingsRoutes.HtmlRunner.route) { HtmlRunnerSettings() }
        composable(SettingsRoutes.LspSettings.route) { LspSettings() }
        composable(SettingsRoutes.Themes.route) { ThemeScreen() }
        composable(SettingsRoutes.Extensions.route) { Extensions() }
        composable(SettingsRoutes.Git.route) { GitSettings() }
    }
}
