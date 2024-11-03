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
import com.rk.xededitor.ui.screens.settings.editor.SettingsEditorScreen
import com.rk.xededitor.ui.screens.settings.git.SettingsGitScreen
import com.rk.xededitor.ui.screens.settings.terminal.SettingsTerminalScreen
import soup.compose.material.motion.animation.rememberSlideDistance

@Composable
fun SettingsNavHost(navController: NavHostController, activity: Activity) {
    val slideDistance = rememberSlideDistance()
    NavHost(
        navController = navController,
        startDestination = SettingsRoutes.Settings.route,
        enterTransition = { NavigationAnimationTransitions.enterTransition(slideDistance) },
        exitTransition = { NavigationAnimationTransitions.exitTransition(slideDistance) },
        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition(slideDistance) },
        popExitTransition = { NavigationAnimationTransitions.popExitTransition(slideDistance) },
    ) {
        composable(SettingsRoutes.Settings.route) { SettingsScreen(navController) }
        composable(SettingsRoutes.AppSettings.route) { SettingsAppScreen() }
        composable(SettingsRoutes.EditorSettings.route) { SettingsEditorScreen() }
        composable(SettingsRoutes.TerminalSettings.route) { SettingsTerminalScreen() }
        composable(SettingsRoutes.GitSettings.route) { SettingsGitScreen() }
        composable(SettingsRoutes.About.route) { AboutScreen() }
    }
}
