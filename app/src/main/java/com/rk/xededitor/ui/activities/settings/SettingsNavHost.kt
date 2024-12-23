package com.rk.xededitor.ui.activities.settings


import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rk.xededitor.ui.animations.NavigationAnimationTransitions
import com.rk.xededitor.ui.screens.settings.SettingsScreen
import com.rk.xededitor.ui.screens.settings.about.AboutScreen
import com.rk.xededitor.ui.screens.settings.app.SettingsAppScreen
import com.rk.xededitor.ui.screens.settings.editor.DefaultEncoding
import com.rk.xededitor.ui.screens.settings.editor.EditorFontScreen
import com.rk.xededitor.ui.screens.settings.editor.SettingsEditorScreen
import com.rk.xededitor.ui.screens.settings.git.SettingsGitScreen
import com.rk.xededitor.ui.screens.settings.mutators.ManageMutators
import com.rk.xededitor.ui.screens.settings.terminal.SettingsTerminalScreen

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
        composable(SettingsRoutes.AppSettings.route) { SettingsAppScreen(activity) }
        composable(SettingsRoutes.EditorSettings.route) { SettingsEditorScreen(navController) }
        composable(SettingsRoutes.TerminalSettings.route) { SettingsTerminalScreen(navController) }
        composable(SettingsRoutes.GitSettings.route) { SettingsGitScreen() }
        composable(SettingsRoutes.About.route) { AboutScreen() }
        composable(SettingsRoutes.EditorFontScreen.route) { EditorFontScreen() }
        composable(SettingsRoutes.DefaultEncoding.route) { DefaultEncoding() }
        composable(SettingsRoutes.ManageMutators.route) { ManageMutators(navController = navController) }
    }
}
