package com.rk.xededitor.ui.activities.settings


import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rk.xededitor.ui.animations.NavigationAnimationTransitions
import com.rk.xededitor.ui.screens.debugger.Debugger
import com.rk.xededitor.ui.screens.settings.SettingsScreen
import com.rk.xededitor.ui.screens.settings.about.AboutScreen
import com.rk.xededitor.ui.screens.settings.app.SettingsAppScreen
import com.rk.xededitor.ui.screens.settings.developer_options.DeveloperOptions
import com.rk.xededitor.ui.screens.settings.editor.DefaultEncoding
import com.rk.xededitor.ui.screens.settings.editor.EditorFontScreen
import com.rk.xededitor.ui.screens.settings.editor.SettingsEditorScreen
import com.rk.xededitor.ui.screens.settings.extensions.Extensions
import com.rk.xededitor.ui.screens.settings.language.LanguageScreen
import com.rk.xededitor.ui.screens.settings.mutators.ManageMutators
import com.rk.xededitor.ui.screens.settings.runners.Runners
import com.rk.xededitor.ui.screens.settings.support.Support
import com.rk.xededitor.ui.screens.settings.terminal.SettingsTerminalScreen
import com.rk.xededitor.ui.screens.settings.theme.ThemeScreen

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
        composable(SettingsRoutes.AppSettings.route) { SettingsAppScreen(activity,navController) }
        composable(SettingsRoutes.EditorSettings.route) { SettingsEditorScreen(navController) }
        composable(SettingsRoutes.TerminalSettings.route) { SettingsTerminalScreen() }
        composable(SettingsRoutes.About.route) { AboutScreen() }
        composable(SettingsRoutes.EditorFontScreen.route) { EditorFontScreen() }
        composable(SettingsRoutes.DefaultEncoding.route) { DefaultEncoding() }
        composable(SettingsRoutes.Extensions.route){ Extensions() }
        composable(SettingsRoutes.DeveloperOptions.route){ DeveloperOptions(navController = navController) }
        composable(SettingsRoutes.BeanshellREPL.route){ Debugger() }
        composable(SettingsRoutes.ManageMutators.route) { ManageMutators(navController = navController) }
        composable(SettingsRoutes.Support.route){ Support() }
        composable(SettingsRoutes.LanguageScreen.route){ LanguageScreen() }
        composable(SettingsRoutes.Runners.route){ Runners() }
        composable(SettingsRoutes.Themes.route) {
            ThemeScreen()
        }
    }
}
