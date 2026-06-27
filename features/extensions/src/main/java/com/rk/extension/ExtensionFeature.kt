package com.rk.extension

import android.app.Application
import androidx.navigation.NavController
import androidx.navigation.NavType
import com.rk.App
import com.rk.activities.settings.SettingsRoutes
import com.rk.extension.loader.loadAllExtensions
import com.rk.extension.manager.ExtensionAPIManager
import com.rk.feature.Feature
import com.rk.feature.SettingsRegistry
import com.rk.feature.SettingsCategory
import com.rk.feature.SettingsRoute
import com.rk.resources.strings
import com.rk.resources.drawables
import com.rk.settings.extension.ExtensionScreen
import com.rk.settings.extension.ExtensionDetail
import com.rk.settings.extension.ExtensionSettings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ExtensionFeature : Feature {
    @OptIn(DelicateCoroutinesApi::class)
    override fun init(application: Application) {
        extensionManager = com.rk.extension.manager.ExtensionManager(application)

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
                route = SettingsRoutes.Extensions.route
            )
        )

        // Register settings routes
        SettingsRegistry.registerRoute(
            SettingsRoute(SettingsRoutes.Extensions.route) { navController ->
                ExtensionScreen(navController = navController)
            }
        )
        SettingsRegistry.registerRoute(
            SettingsRoute("${SettingsRoutes.ExtensionDetail.route}/{extensionId}") { navController ->
                val it = navController.currentBackStackEntry
                val extensionId = it?.arguments?.getString("extensionId")
                val extension = extensionId?.let { extensionManager.getExtension(it) }
                ExtensionDetail(extension, navController)
            }
        )
        SettingsRegistry.registerRoute(
            SettingsRoute("${SettingsRoutes.ExtensionSettings.route}/{extensionId}") { navController ->
                val it = navController.currentBackStackEntry
                val extensionId = it?.arguments?.getString("extensionId")
                val extension = extensionId?.let { extensionManager.getExtension(it) }
                ExtensionSettings(extension)
            }
        )
    }
}
