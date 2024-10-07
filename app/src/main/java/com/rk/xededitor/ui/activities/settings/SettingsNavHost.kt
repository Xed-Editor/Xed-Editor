package com.rk.xededitor.ui.activities.settings

import android.content.Context

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

import com.rk.xededitor.ui.screens.settings.SettingsScreen
import com.rk.xededitor.ui.screens.settings.app.SettingsAppScreen
import com.rk.xededitor.ui.screens.settings.editor.SettingsEditorScreen
import com.rk.xededitor.ui.screens.settings.git.SettingsGitScreen
import com.rk.xededitor.ui.screens.settings.terminal.SettingsTerminalScreen
import com.rk.xededitor.ui.screens.settings.manage.ManagePluginsScreen

@Composable
fun SettingsNavHost(
    navController: NavHostController,
    context: Context
) {
    NavHost(navController = navController, startDestination = SettingsRoutes.Settings.route) {
        composable(SettingsRoutes.Settings.route) {
            SettingsScreen(navController)
        }
        composable(SettingsRoutes.AppSettings.route) {
            SettingsAppScreen()
        }
        composable(SettingsRoutes.EditorSettings.route) {
            SettingsEditorScreen()
        }
        composable(SettingsRoutes.TerminalSettings.route) {
            SettingsTerminalScreen()
        }
        composable(SettingsRoutes.GitSettings.route) {
            SettingsGitScreen()
        }
        composable(SettingsRoutes.ManagePlugins.route) {
            ManagePluginsScreen(
               onBackPressed = {
                   navController.popBackStack()
               },
               context = context
            )
        }
    }
}