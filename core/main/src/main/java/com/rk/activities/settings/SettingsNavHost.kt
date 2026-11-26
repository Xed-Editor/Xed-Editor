package com.rk.activities.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.ComposeNavigatorDestinationBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import com.rk.animations.NavigationAnimationTransitions
import com.rk.extension.api.DynamicRoute
import com.rk.lsp.LspRegistry
import com.rk.settings.SettingsRegistry
import com.rk.settings.SettingsScreen
import com.rk.settings.StorageDistributionScreen
import com.rk.settings.about.AboutScreen
import com.rk.settings.account.AccountScreen
import com.rk.settings.app.SettingsAppScreen
import com.rk.settings.debugOptions.AppLogs
import com.rk.settings.debugOptions.DeveloperOptions
import com.rk.settings.editor.AppFontScreen
import com.rk.settings.editor.DefaultEncoding
import com.rk.settings.editor.DefaultLineEnding
import com.rk.settings.editor.EditExtraKeys
import com.rk.settings.editor.EditToolbarActions
import com.rk.settings.editor.EditorFontScreen
import com.rk.settings.editor.ExcludeFiles
import com.rk.settings.editor.FormatterSettings
import com.rk.settings.editor.SettingsEditorScreen
import com.rk.settings.keybinds.KeybindingsScreen
import com.rk.settings.language.LanguageScreen
import com.rk.settings.lsp.LspInitializationOptions
import com.rk.settings.lsp.LspServerDetail
import com.rk.settings.lsp.LspServerLogs
import com.rk.settings.lsp.LspSettings
import com.rk.settings.support.Support
import com.rk.settings.theme.ThemeScreen

@Composable
fun SettingsNavHost(navController: NavHostController, activity: SettingsActivity) {
    val customRoutes by SettingsRegistry.routes.collectAsStateWithLifecycle()

    // NavHost remembers its graph, so a destination registered after the graph was created (e.g. when a
    // feature is switched on from App settings) would never be reachable and navigating to it would throw.
    // Build the graph from the routes available now and inject the ones registered later below.
    val graph =
        remember(navController, activity) {
            navController.createGraph(startDestination = SettingsRoutes.Settings.route) {
                composable(SettingsRoutes.Settings.route) { SettingsScreen(navController) }
                composable(SettingsRoutes.Account.route) { AccountScreen() }
                composable(SettingsRoutes.AppSettings.route) { SettingsAppScreen(activity, navController) }
                composable(SettingsRoutes.EditorSettings.route) { SettingsEditorScreen(navController) }
                composable(SettingsRoutes.Keybindings.route) { KeybindingsScreen() }

                composable(SettingsRoutes.About.route) { AboutScreen() }
                composable(SettingsRoutes.EditorFontScreen.route) { EditorFontScreen() }
                composable(SettingsRoutes.AppFontScreen.route) { AppFontScreen() }

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
                composable(SettingsRoutes.AppLogs.route) { AppLogs() }
                composable(SettingsRoutes.Support.route) { Support() }
                composable(SettingsRoutes.LanguageScreen.route) { LanguageScreen() }
                composable(SettingsRoutes.Formatters.route) { FormatterSettings(navController) }
                composable(SettingsRoutes.LspSettings.route) { LspSettings(navController) }
                composable(SettingsRoutes.StorageSettings.route) { StorageDistributionScreen() }
                composable(
                    "${SettingsRoutes.LspServerDetail.route}/{serverId}",
                    arguments = listOf(navArgument("serverId", builder = { type = NavType.StringType })),
                ) { backStackEntry ->
                    val serverId = backStackEntry.arguments?.getString("serverId")!!
                    val server = LspRegistry.getForId(serverId)!!
                    LspServerDetail(navController, server)
                }
                composable(
                    "${SettingsRoutes.LspInitializationOptions.route}/{serverId}",
                    arguments = listOf(navArgument("serverId", builder = { type = NavType.StringType })),
                ) { backStackEntry ->
                    val serverId = backStackEntry.arguments?.getString("serverId")!!
                    val server = LspRegistry.getForId(serverId)!!
                    LspInitializationOptions(server)
                }
                composable(
                    "${SettingsRoutes.LspServerLogs.route}/{serverId}/{instanceId}",
                    arguments =
                        listOf(
                            navArgument("serverId", builder = { type = NavType.StringType }),
                            navArgument("instanceId", builder = { type = NavType.StringType }),
                        ),
                ) { backStackEntry ->
                    val serverId = backStackEntry.arguments?.getString("serverId")!!
                    val server = LspRegistry.getForId(serverId)!!
                    val instanceId = backStackEntry.arguments?.getString("instanceId")!!
                    LspServerLogs(server, instanceId)
                }
                composable(SettingsRoutes.Themes.route) { ThemeScreen(navController) }

                SettingsRegistry.routes.value.forEach { customRoute ->
                    composable(customRoute.route, arguments = customRoute.arguments) { backStackEntry ->
                        customRoute.content(navController, backStackEntry)
                    }
                }
            }
        }

    NavHost(
        navController = navController,
        graph = graph,
        enterTransition = { NavigationAnimationTransitions.enterTransition },
        exitTransition = { NavigationAnimationTransitions.exitTransition },
        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition },
        popExitTransition = { NavigationAnimationTransitions.popExitTransition },
    )

    // Add destinations for routes contributed after the graph was built, without recreating the graph
    // (recreating it would reset the current back stack).
    LaunchedEffect(customRoutes) {
        customRoutes.forEach { customRoute ->
            if (graph.findNode(customRoute.route) == null) {
                graph.addDestination(customRoute.toComposeDestination(navController))
            }
        }
    }
}

private fun DynamicRoute.toComposeDestination(navController: NavController): NavDestination {
    val navigator = navController.navigatorProvider.getNavigator(ComposeNavigator::class.java)
    return ComposeNavigatorDestinationBuilder(navigator, route) { backStackEntry ->
            content(navController, backStackEntry)
        }
        .apply { arguments.forEach { argument(it.name, it.argument) } }
        .build()
}
