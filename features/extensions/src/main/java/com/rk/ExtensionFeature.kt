package com.rk

import android.app.Application
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.rk.App.Companion.iconPackManager
import com.rk.App.Companion.themeManager
import com.rk.activities.main.MainActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.common.XedPackage
import com.rk.components.DialogProvider
import com.rk.components.DialogRegistry
import com.rk.extension.ActivityProvider
import com.rk.extension.api.DynamicRoute
import com.rk.extension.api.IntentHandleRegistry
import com.rk.extension.api.IntentHandler
import com.rk.extension.extensionManager
import com.rk.extension.loader.loadAllExtensions
import com.rk.extension.loader.unloadAllExtensions
import com.rk.extension.manager.ExtensionAPIManager
import com.rk.extension.manager.ExtensionManager
import com.rk.extension.ui.XedInstallDialog
import com.rk.feature.Feature
import com.rk.feature.FeatureToggle
import com.rk.file.FileWrapper
import com.rk.file.copyToTempDir
import com.rk.filetree.isXedPackage
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.SettingsCategory
import com.rk.settings.SettingsRegistry
import com.rk.settings.extension.ExtensionSettings
import com.rk.settings.extension.PackageDetail
import com.rk.settings.extension.StoreScreen
import com.rk.utils.errorDialog
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExtensionFeature : Feature {
    override val toggle =
        FeatureToggle(
            nameRes = strings.ext,
            key = "enable_extension",
            default = true,
            iconRes = drawables.extension,
        )

    private var intentHandler: IntentHandler? = null
    private var dialogProvider: DialogProvider? = null
    private var settingsCategory: SettingsCategory? = null
    private val routes = mutableListOf<DynamicRoute>()

    @OptIn(DelicateCoroutinesApi::class)
    override fun init(application: Application) {
        extensionManager = ExtensionManager(application)

        intentHandler =
            IntentHandler { file ->
                if (!file.isXedPackage()) return@IntentHandler false

                withContext(Dispatchers.IO) {
                    val context = application.applicationContext
                    val cacheDir = context.cacheDir
                    val tempFile = file.copyToTempDir()
                    val tempDir = File(cacheDir, "ext_temp_${System.currentTimeMillis()}")
                    tempDir.mkdirs()
                    try {
                        XedPackage.extract(tempFile, tempDir)
                        extensionManager
                            .validateExtensionDir(tempDir)
                            .onSuccess { manifest ->
                                val packageIcon = tempDir.resolve("icon.png")
                                val iconFile = FileWrapper(packageIcon).copyToTempDir()

                                withContext(Dispatchers.Main) {
                                    MainActivity.instance
                                        ?.viewModel
                                        ?.openExtensionIntentDialog(manifest, tempFile, iconFile)
                                }
                            }
                            .onFailure {
                                withContext(Dispatchers.Main) {
                                    errorDialog(throwable = it)
                                }
                            }
                    } finally {
                        tempDir.deleteRecursively()
                    }
                }
                return@IntentHandler true
            }
                .also { IntentHandleRegistry.register(it) }

        dialogProvider =
            DialogProvider {
                MainActivity.instance?.let {
                    val viewModel = it.viewModel
                    val manifest = viewModel.pendingExtensionManifest ?: return@let
                    val packageFile = viewModel.pendingExtensionPackage ?: return@let
                    val icon = viewModel.pendingExtensionIcon ?: return@let
                    XedInstallDialog(manifest, icon, packageFile, viewModel::closeExtensionIntentDialog)
                }
            }
                .also { DialogRegistry.register(it) }

        // Initialize and load extensions
        GlobalScope.launch(Dispatchers.IO) {
            application.registerActivityLifecycleCallbacks(ExtensionAPIManager)
            application.registerActivityLifecycleCallbacks(ActivityProvider)

            extensionManager.indexLocalExtensions()
            extensionManager.loadAllExtensions()
            extensionManager.indexStoreExtensions()
        }

        // Register settings category
        settingsCategory =
            SettingsCategory(
                    labelRes = strings.store,
                    descriptionRes = strings.store_desc,
                    iconRes = drawables.store,
                    route = SettingsRoutes.Extensions.route,
                )
                .also { SettingsRegistry.registerCategory(it) }

        // Register settings routes
        routes.add(
            DynamicRoute(
                "${SettingsRoutes.Extensions.route}?query={query}&category={category}",
                arguments =
                    listOf(
                        navArgument(
                            "query",
                            builder = {
                                nullable = true
                                type = NavType.StringType
                            },
                        ),
                        navArgument(
                            "category",
                            builder = {
                                nullable = true
                                type = NavType.StringType
                            },
                        ),
                    ),
            ) { navController, backStackEntry ->
                val query = backStackEntry.arguments?.getString("query")
                val category = backStackEntry.arguments?.getString("category")
                StoreScreen(navController = navController, query = query, category = category)
            }
        )
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
                val extension = extensionId?.let { extensionManager.installedExtensions[it] }
                ExtensionSettings(extension)
            }
        )
        routes.add(
            DynamicRoute("${SettingsRoutes.ThemeDetail.route}/{themeId}") { navController, backStackEntry ->
                val themeId = backStackEntry.arguments?.getString("themeId")
                val theme = themeId?.let { themeManager.getTheme(it) }
                PackageDetail(theme, navController)
            }
        )
        routes.add(
            DynamicRoute("${SettingsRoutes.IconPackDetail.route}/{iconPackId}") { navController, backStackEntry ->
                val iconPackId = backStackEntry.arguments?.getString("iconPackId")
                val iconPack = iconPackId?.let { iconPackManager.getIconPackPackage(it) }
                PackageDetail(iconPack, navController)
            }
        )

        routes.forEach { SettingsRegistry.registerRoute(it) }
    }

    override fun dispose(application: Application) {
        extensionManager.unloadAllExtensions()
        application.unregisterActivityLifecycleCallbacks(ExtensionAPIManager)
        application.unregisterActivityLifecycleCallbacks(ActivityProvider)

        intentHandler?.let { IntentHandleRegistry.unregister(it) }
        dialogProvider?.let { DialogRegistry.unregister(it) }
        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        routes.forEach { SettingsRegistry.unregisterRoute(it) }
        routes.clear()
    }
}
