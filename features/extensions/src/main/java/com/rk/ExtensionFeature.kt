package com.rk

import android.app.Application
import com.rk.activities.settings.SettingsRoutes
import com.rk.extension.api.DynamicRoute
import com.rk.extension.extensionManager
import com.rk.extension.loader.loadAllExtensions
import com.rk.extension.loader.unloadAllExtensions
import com.rk.extension.manager.ExtensionAPIManager
import com.rk.extension.manager.ExtensionManager
import com.rk.extension.store.ExtensionStoreSection
import com.rk.feature.Feature
import com.rk.feature.FeatureToggle
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.SettingsRegistry
import com.rk.settings.extension.ExtensionSettings
import com.rk.store.StoreRegistry
import com.rk.store.ui.PackageDetail
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Extensions: the runtime that loads extension code, plus the extension catalog contributed to the store.
 *
 * The store itself lives in `:features:store`, so disabling extensions (or building without this module) no longer
 * removes the theme and icon pack catalogs, package detail screens or install-from-storage flows.
 */
class ExtensionFeature : Feature {
    override val toggle =
        FeatureToggle(
            name = strings.ext.getString(),
            key = "enable_extension",
            default = true,
            icon = Icon.ResourceIcon(drawables.extension),
        )

    private val routes = mutableListOf<DynamicRoute>()
    private val storeSection = ExtensionStoreSection()

    @OptIn(DelicateCoroutinesApi::class)
    override fun init(application: Application) {
        extensionManager = ExtensionManager(application)

        // Initialize and load extensions
        GlobalScope.launch(Dispatchers.IO) {
            application.registerActivityLifecycleCallbacks(ExtensionAPIManager)

            extensionManager.indexLocalExtensions()
            extensionManager.loadAllExtensions()
            extensionManager.indexStoreExtensions()
        }

        // Contribute the extension catalog and its install/update/uninstall flow to the store.
        StoreRegistry.register(storeSection)

        routes.add(
            DynamicRoute("${SettingsRoutes.ExtensionDetail.route}/{extensionId}") { navController, backStackEntry ->
                val extensionId = backStackEntry.arguments?.getString("extensionId")
                val extension = extensionId?.let { extensionManager.getExtension(it) }
                PackageDetail(extension, navController)
            }
        )
        routes.add(
            DynamicRoute("${SettingsRoutes.ExtensionSettings.route}/{extensionId}") { _, backStackEntry ->
                val extensionId = backStackEntry.arguments?.getString("extensionId")
                val extension = extensionId?.let { extensionManager.installedExtensions.value[it] }
                ExtensionSettings(extension)
            }
        )

        routes.forEach { SettingsRegistry.registerRoute(it) }
    }

    override fun dispose(application: Application) {
        // `ActivityProvider` is owned by `App`, not by this feature: unregistering it here would break core dialogs
        // whenever extensions are switched off.
        extensionManager.unloadAllExtensions()
        application.unregisterActivityLifecycleCallbacks(ExtensionAPIManager)

        StoreRegistry.unregister(storeSection)

        routes.forEach { SettingsRegistry.unregisterRoute(it) }
        routes.clear()
    }
}
