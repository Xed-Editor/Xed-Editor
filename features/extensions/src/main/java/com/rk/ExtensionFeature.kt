package com.rk

import android.app.Application
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.rk.activities.settings.SettingsRoutes
import com.rk.extension.ActivityProvider
import com.rk.extension.extensionManager
import com.rk.extension.loader.loadAllExtensions
import com.rk.extension.manager.ExtensionAPIManager
import com.rk.extension.manager.ExtensionManager
import com.rk.feature.Feature
import com.rk.feature.FeatureToggle
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.SettingsCategory
import com.rk.settings.SettingsRegistry
import com.rk.extension.api.DynamicRoute
import com.rk.settings.extension.ExtensionDetail
import com.rk.settings.extension.ExtensionScreen
import com.rk.settings.extension.ExtensionSettings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ExtensionFeature : Feature {
    override val toggle =
        FeatureToggle(
            nameRes = strings.ext,
            key = "enable_extension",
            default = true,
            iconRes = drawables.extension,
        )

    @OptIn(DelicateCoroutinesApi::class)
    override fun init(application: Application) {
        extensionManager = ExtensionManager(application)

        // Initialize and load extensions
        GlobalScope.launch(Dispatchers.IO) {
            extensionManager.indexLocalExtensions()
            extensionManager.loadAllExtensions()
            application.registerActivityLifecycleCallbacks(ExtensionAPIManager)
            application.registerActivityLifecycleCallbacks(ActivityProvider)
        }

        // Register settings category
        SettingsRegistry.registerCategory(
            SettingsCategory(
                labelRes = strings.store,
                descriptionRes = strings.store_desc,
                iconRes = drawables.store,
                route = SettingsRoutes.Extensions.route,
            )
        )

        // Register settings routes
        SettingsRegistry.registerRoute(
            DynamicRoute(
                "${SettingsRoutes.Extensions.route}?query={query}",
                arguments =
                    listOf(
                        navArgument(
                            "query",
                            builder = {
                                nullable = true
                                type = NavType.StringType
                            },
                        )
                    ),
            ) { navController, backStackEntry ->
                val query = backStackEntry.arguments?.getString("query")
                ExtensionScreen(navController = navController, query)
            }
        )
        SettingsRegistry.registerRoute(
            DynamicRoute("${SettingsRoutes.ExtensionDetail.route}/{extensionId}") { navController, backStackEntry ->
                val extensionId = backStackEntry.arguments?.getString("extensionId")
                val extension = extensionId?.let { extensionManager.getExtension(it) }
                ExtensionDetail(extension, navController)
            }
        )
        SettingsRegistry.registerRoute(
            DynamicRoute("${SettingsRoutes.ExtensionSettings.route}/{extensionId}") { _, backStackEntry ->
                val extensionId = backStackEntry.arguments?.getString("extensionId")
                val extension = extensionId?.let { extensionManager.getExtension(it) }
                ExtensionSettings(extension)
            }
        )
    }
}
